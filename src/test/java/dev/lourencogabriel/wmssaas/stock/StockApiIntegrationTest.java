package dev.lourencogabriel.wmssaas.stock;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.*;
import dev.lourencogabriel.wmssaas.stock.repository.*;
import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.order.model.*;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:stock-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StockApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired StockRepository stocks;
    @Autowired LpnRepository lpns;
    @Autowired StockTransactionRepository ledger;
    TransactionTemplate tx;
    String managerUserId,manager,operator,stockId,secondStockId,originId,destinationId,warehouseId,lpnId,sku,lpnCode,originCode,destinationCode,productId;

    @BeforeEach void setup() {
        tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            String suffix=UUID.randomUUID().toString();
            manager=actor("GESTOR",suffix); operator=actor("OPERADOR",suffix);
            Warehouse warehouse=new Warehouse();warehouse.setName("Stock "+suffix);em.persist(warehouse);warehouseId=warehouse.getId();
            Zone zone=new Zone();zone.setWarehouseId(warehouseId);zone.setName("Storage");em.persist(zone);
            Position origin=position(zone,"FROM-"+suffix);originId=origin.getId();originCode=origin.getCode();
            Position destination=position(zone,"TO-"+suffix);destinationId=destination.getId();destinationCode=destination.getCode();
            Category category=new Category();category.setName("Stock");em.persist(category);
            Product product=new Product();product.setCategoryId(category.getId());product.setName("Test product");
            sku="SKU-"+suffix;product.setSku(sku);product.setUnitWeightKg(BigDecimal.ONE);product.setUnitVolumeM3(BigDecimal.ONE);em.persist(product);productId=product.getId();
            Lpn lpn=new Lpn();lpnCode="LPN-"+suffix;lpn.setTrackingCode(lpnCode);lpn.setPositionId(originId);lpn.setStatus("ATIVO");em.persist(lpn);lpnId=lpn.getId();
            Stock first=balance(product,lpn,"6");stockId=first.getId();
            Stock second=balance(product,lpn,"4");secondStockId=second.getId();
            em.flush();
        });
    }
    @Test void transfersEveryLayerOfAWholeLpnAndReplaysWithoutDuplicatingLedger() throws Exception {
        String task=createTransfer(Map.of("lpnId",lpnId,"destinationPositionId",destinationId,"quantity",10));
        var request=confirmation(10,lpnCode);
        String key=UUID.randomUUID().toString();
        command("/api/transfers/"+task+"/confirm",request,key,operator)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONCLUIDA"));
        command("/api/transfers/"+task+"/confirm",request,key,operator).andExpect(status().isOk());
        assertEquals(destinationId,stocks.findById(stockId).orElseThrow().getPositionId());
        assertEquals(destinationId,stocks.findById(secondStockId).orElseThrow().getPositionId());
        assertEquals(destinationId,lpns.findById(lpnId).orElseThrow().getPositionId());
        assertEquals(1,ledger.findByStockIdOrderByOccurredAtAsc(stockId).size());
        assertEquals(1,ledger.findByStockIdOrderByOccurredAtAsc(secondStockId).size());
    }
    @Test void badScanRollsBackAndASplitTransferPreservesTheOriginalEntryLayer() throws Exception {
        String entry=stocks.findById(stockId).orElseThrow().getEntryAt().toString();
        String task=createTransfer(Map.of("stockId",stockId,"destinationPositionId",destinationId,"destinationLpnCode",lpnCode+"-SPLIT","quantity",2));
        command("/api/transfers/"+task+"/confirm",confirmation(2,"WRONG"),UUID.randomUUID().toString(),operator)
                .andExpect(status().isUnprocessableContent());
        assertEquals(0,new BigDecimal("6").compareTo(stocks.findById(stockId).orElseThrow().getQuantity()));
        assertTrue(ledger.findByStockIdOrderByOccurredAtAsc(stockId).isEmpty());
        command("/api/transfers/"+task+"/confirm",confirmation(2,lpnCode),UUID.randomUUID().toString(),operator).andExpect(status().isOk());
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(stockId).orElseThrow().getQuantity()));
        Stock target=stocks.findByLpnId(lpns.findByTrackingCode(lpnCode+"-SPLIT").orElseThrow().getId()).getFirst();
        assertEquals(entry,target.getEntryAt().toString());assertEquals(destinationId,target.getPositionId());
        assertEquals(0,new BigDecimal("2").compareTo(target.getQuantity()));
    }
    @Test void lotAndPositionBlocksRemainEffectiveUntilAllApplicableBlocksAreReleased() throws Exception {
        tx.executeWithoutResult(s->{for(Stock stock:stocks.findByLpnId(lpnId)){stock.setLot("LOT-A");stocks.save(stock);}});
        String block=dataId(command("/api/stock-blocks/lots",Map.of("warehouseId",warehouseId,"productId",productId,"lot","LOT-A","reason","Quality"),UUID.randomUUID().toString(),manager).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/stock/{id}",stockId).header("Authorization",operator)).andExpect(status().isOk()).andExpect(jsonPath("$.data.availableQuantity").value(0));
        command("/api/positions/"+originId+"/block",Map.of("reason","Inspection"),UUID.randomUUID().toString(),manager).andExpect(status().isOk());
        command("/api/stock-blocks/"+block+"/release",Map.of("reason","Lot approved"),UUID.randomUUID().toString(),manager).andExpect(status().isOk());
        mvc.perform(get("/api/stock/{id}",stockId).header("Authorization",operator)).andExpect(jsonPath("$.data.availableQuantity").value(0));
        command("/api/positions/"+originId+"/unblock",Map.of("reason","Position approved"),UUID.randomUUID().toString(),manager).andExpect(status().isOk());
        mvc.perform(get("/api/stock/{id}",stockId).header("Authorization",operator)).andExpect(jsonPath("$.data.availableQuantity").value(6));
    }
    @Test void missingKeysAndUnauthorizedLedgerWritesAreRejected() throws Exception {
        mvc.perform(post("/api/stock/{id}/block",stockId).header("Authorization",manager).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"hold\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/stock-transactions/test").header("Authorization",manager).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        command("/api/stock/"+stockId+"/block",Map.of("reason","hold"),"key",operator).andExpect(status().isForbidden());
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/health/ready")).andExpect(status().isOk());
    }
    @Test void cancelledPreparedGoodsReturnPhysicallyAndRestoreTheOrderPreparedCounter() throws Exception {
        String orderId=tx.execute(s->{
            Partner p=new Partner();p.setLegalName("Customer");p.setType("CLIENTE");em.persist(p);
            WarehouseOrder order=new WarehouseOrder();order.setWarehouseId(warehouseId);order.setPartnerId(p.getId());order.setType("SAIDA");order.setStatus("CANCELADO");order.setCreatedById(managerUserId);
            em.persist(order);
            OrderItem item=new OrderItem();item.setOrderId(order.getId());item.setProductId(productId);item.setRequestedQuantity(BigDecimal.TEN);item.setPickedQuantity(BigDecimal.TEN);em.persist(item);
            Lpn lpn=lpns.findById(lpnId).orElseThrow();lpn.setOutboundOrderId(order.getId());lpns.save(lpn);return order.getId();
        });
        String task=createTransfer(Map.of("lpnId",lpnId,"destinationPositionId",destinationId,"quantity",10));
        command("/api/transfers/"+task+"/confirm",confirmation(10,lpnCode),UUID.randomUUID().toString(),operator).andExpect(status().isOk());
        assertNull(lpns.findById(lpnId).orElseThrow().getOutboundOrderId());
        tx.executeWithoutResult(s->assertEquals(0,em.createQuery("select i from OrderItem i where i.orderId=:id",OrderItem.class).setParameter("id",orderId).getSingleResult().getPickedQuantity().signum()));
        assertEquals("RETORNO",ledger.findByStockIdOrderByOccurredAtAsc(stockId).getFirst().getMovementType());
    }
    @Test void rejectsAWholeLpnPlanWhoseOriginChangedAfterAnotherTransfer() throws Exception {
        String thirdPositionId=tx.execute(status->{
            Position original=em.find(Position.class,originId);
            Zone zone=em.find(Zone.class,original.getZoneId());
            return position(zone,"THIRD-"+UUID.randomUUID()).getId();
        });
        String thirdCode=tx.execute(status->em.find(Position.class,thirdPositionId).getCode());
        String firstPlan=createTransfer(Map.of("lpnId",lpnId,"destinationPositionId",destinationId,"quantity",10));
        String obsoletePlan=createTransfer(Map.of("lpnId",lpnId,"destinationPositionId",thirdPositionId,"quantity",10));
        command("/api/transfers/"+firstPlan+"/confirm",confirmation(10,lpnCode),UUID.randomUUID().toString(),operator)
                .andExpect(status().isOk());
        long ledgerCount=ledger.count();
        var obsoleteScan=new LinkedHashMap<>(confirmation(10,lpnCode));
        obsoleteScan.put("scannedDestinationCode",thirdCode);
        command("/api/transfers/"+obsoletePlan+"/confirm",obsoleteScan,UUID.randomUUID().toString(),operator)
                .andExpect(status().isUnprocessableContent());
        assertEquals(destinationId,lpns.findById(lpnId).orElseThrow().getPositionId());
        assertEquals(destinationId,stocks.findById(stockId).orElseThrow().getPositionId());
        assertEquals(destinationId,stocks.findById(secondStockId).orElseThrow().getPositionId());
        assertEquals(ledgerCount,ledger.count());
        assertTrue(ledger.findByTaskId(obsoletePlan).isEmpty());
        tx.executeWithoutResult(status->assertEquals("PENDENTE",em.find(WarehouseTask.class,obsoletePlan).getStatus()));
    }

    @Test void compensationAppendsOneEntryPreservesTheOriginalAndReplaysWithoutChangingStockAgain() throws Exception {
        String originalId=tx.execute(status->{
            WmsOperation operation=new WmsOperation();operation.setIdempotencyKey(UUID.randomUUID().toString());
            operation.setType("ENTRADA");operation.setUserId(managerUserId);operation.setRequestHash("f".repeat(64));
            operation.setStatus("CONCLUIDA");operation.setCompletedAt(Instant.now());operation.setResult("{}");em.persist(operation);
            Stock balance=em.find(Stock.class,stockId);
            StockTransaction entry=new StockTransaction();entry.setProductId(productId);entry.setUserId(managerUserId);
            entry.setStockId(stockId);entry.setLpnId(lpnId);entry.setOperationId(operation.getId());entry.setSequence(1);
            entry.setMovementType("ENTRADA");entry.setQuantity(new BigDecimal("6"));entry.setPreviousBalance(BigDecimal.ZERO);
            entry.setResultingBalance(new BigDecimal("6"));entry.setNewPositionId(originId);entry.setOccurredAt(Instant.now());
            entry.setRecordedEntryAt(balance.getEntryAt());em.persist(entry);em.flush();return entry.getId();
        });
        String snapshot=mapper.writeValueAsString(ledger.findById(originalId).orElseThrow());
        Map<String,Object> request=Map.of("quantity",2,"reason","Correção de recebimento",
                "scannedLpnCode",lpnCode,"scannedPositionCode",originCode,"scannedProductCode",sku);
        String url="/api/stock-transactions/"+originalId+"/compensations",key=UUID.randomUUID().toString();
        String first=command(url,request,key,manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movementType").value("COMPENSACAO"))
                .andExpect(jsonPath("$.data.compensatedTransactionId").value(originalId))
                .andExpect(jsonPath("$.data.quantity").value(-2)).andReturn().getResponse().getContentAsString();
        String replay=command(url,request,key,manager).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(dataId(first),dataId(replay));
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(stockId).orElseThrow().getQuantity()));
        assertEquals(snapshot,mapper.writeValueAsString(ledger.findById(originalId).orElseThrow()));
        assertEquals(1,ledger.findByCompensatedTransactionId(originalId).size());
        assertEquals(2,ledger.findByStockIdOrderByOccurredAtAsc(stockId).size());
        var excessive=new LinkedHashMap<>(request);excessive.put("quantity",5);
        command(url,excessive,UUID.randomUUID().toString(),manager).andExpect(status().isUnprocessableContent());
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(stockId).orElseThrow().getQuantity()));
        assertEquals(1,ledger.findByCompensatedTransactionId(originalId).size());
    }

    private String actor(String profile,String suffix){User u=new User(profile,profile+suffix+"@stock.test","hash");u.setProfile(profiles.findById(profile).orElseThrow());u.setStatus(UserAccountStatus.ACTIVE);u.setEmailVerifiedAt(Instant.now());em.persist(u);em.flush();if ("GESTOR".equals(profile)) managerUserId=u.getUlid();return "Bearer "+tokens.generateTokens(u.getUlid()).token();}
    private Position position(Zone zone,String code){Position p=new Position();p.setZoneId(zone.getId());p.setCode(code);p.setWeightCapacityKg(new BigDecimal("100"));p.setVolumeCapacityM3(new BigDecimal("100"));em.persist(p);return p;}
    private Stock balance(Product p,Lpn lpn,String quantity){Stock s=new Stock();s.setProductId(p.getId());s.setLpnId(lpn.getId());s.setPositionId(lpn.getPositionId());s.setQuantity(new BigDecimal(quantity));s.setEntryAt(Instant.parse("2026-01-01T00:00:00Z"));em.persist(s);return s;}
    private Map<String,Object> confirmation(int qty,String scanned){return Map.of("actualQuantity",qty,"scannedLpnCode",scanned,"scannedOriginCode",originCode,"scannedDestinationCode",destinationCode,"scannedProductCode",sku);}
    private String createTransfer(Object body)throws Exception{return dataId(command("/api/transfers",body,UUID.randomUUID().toString(),operator).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    private String dataId(String body){return mapper.readTree(body).path("data").path("id").asText();}
    private org.springframework.test.web.servlet.ResultActions command(String path,Object body,String key,String actor)throws Exception{return mvc.perform(post(path).header("Authorization",actor).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));}
}
