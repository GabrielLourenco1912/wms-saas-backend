package dev.lourencogabriel.wmssaas.inbound;

import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.inbound.repository.ReceiptConfirmationRepository;
import dev.lourencogabriel.wmssaas.operation.repository.WmsOperationRepository;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.model.ProductPackaging;
import dev.lourencogabriel.wmssaas.product.repository.CategoryRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductPackagingRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.task.repository.TaskHistoryRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.WarehouseRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/service/database transactions: no outer test transaction can hide an incomplete rollback. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inbound-api-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InboundApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired WarehouseRepository warehouses;
    @Autowired ZoneRepository zones;
    @Autowired PositionRepository positions;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductPackagingRepository packaging;
    @Autowired PartnerRepository partners;
    @Autowired WarehouseOrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired StockRepository stocks;
    @Autowired LpnRepository lpns;
    @Autowired StockTransactionRepository ledger;
    @Autowired WarehouseTaskRepository tasks;
    @Autowired TaskHistoryRepository history;
    @Autowired ReceiptConfirmationRepository confirmations;
    @Autowired WmsOperationRepository operations;

    Warehouse warehouse;
    Position dock;
    Position storage;
    Product product;
    ProductPackaging unit;
    Partner supplier;
    String manager;
    String managerId;
    String operator;

    @BeforeEach
    void fixture() {
        String unique=UUID.randomUUID().toString();
        manager=token("GESTOR","inbound-manager-"+unique+"@example.com");
        managerId=users.findByEmail("inbound-manager-"+unique+"@example.com").getUlid();
        operator=token("OPERADOR","inbound-operator-"+unique+"@example.com");
        warehouse=new Warehouse(); warehouse.setName("Inbound "+unique); warehouse=warehouses.saveAndFlush(warehouse);
        Zone zone=new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("Zona A"); zone=zones.saveAndFlush(zone);
        dock=position(zone,"DOCA-ENTRADA","RECEBIMENTO"); storage=position(zone,"A-001","ARMAZENAGEM");
        Category category=new Category(); category.setName("Inbound "+unique); category=categories.saveAndFlush(category);
        product=new Product(); product.setCategoryId(category.getId()); product.setSku("SKU-"+unique); product.setName("Produto de recebimento");
        product.setBaseUnit("UN"); product.setUnitWeightKg(new BigDecimal("1")); product.setUnitVolumeM3(new BigDecimal("0.1"));
        product.setLotControlled(true); product.setExpiryControlled(true); product=products.saveAndFlush(product);
        unit=new ProductPackaging(); unit.setProductId(product.getId()); unit.setType("UN"); unit.setConversionFactor(BigDecimal.ONE);
        unit.setBarcode("EAN-"+unique); unit=packaging.saveAndFlush(unit);
        supplier=new Partner(); supplier.setLegalName("Fornecedor "+unique); supplier.setType("FORNECEDOR"); supplier=partners.saveAndFlush(supplier);
    }

    @Test
    void shouldKeepPreReceiptDocumentaryAndSupportReadListAndEdition() throws Exception {
        PreReceiptDetail created=create("10");
        String id=created.receipt().id();
        assertEquals("PENDENTE",created.receipt().status());
        assertTrue(warehouseStock().isEmpty());
        assertTrue(ledgerFor(created).isEmpty());
        assertTrue(tasks.findByOrderId(id).isEmpty());

        mvc.perform(get("/api/pre-receipts/{id}",id).header("Authorization",manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].sku").value(product.getSku()));
        mvc.perform(get("/api/pre-receipts").param("warehouseId",warehouse.getId()).param("status","PENDENTE").header("Authorization",manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        PreReceiptRequest edit=document("12","NF-EDITADA");
        String response=mvc.perform(put("/api/pre-receipts/{id}",id).header("Authorization",manager)
                        .header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(edit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.receipt.documentNumber").value("NF-EDITADA"))
                .andReturn().getResponse().getContentAsString();
        PreReceiptDetail updated=data(response,PreReceiptDetail.class);
        assertEquals(0,new BigDecimal("12").compareTo(updated.items().getFirst().requestedQuantity()));
        assertTrue(warehouseStock().isEmpty());
    }

    @Test
    void shouldPageSearchAndLookupOnlyMatchingInboundDocumentsAndTasks() throws Exception {
        for (int index=0;index<21;index++) {
            var order=new WarehouseOrder();order.setWarehouseId(warehouse.getId());order.setPartnerId(supplier.getId());
            order.setDocumentNumber("QUERY-NF-"+index);order.setType("ENTRADA");order.setStatus("PENDENTE");order.setCreatedById(managerId);
            order=orders.saveAndFlush(order);
            var item=new OrderItem();item.setOrderId(order.getId());item.setProductId(product.getId());item.setRequestedQuantity(BigDecimal.ONE);orderItems.saveAndFlush(item);
        }
        mvc.perform(get("/api/search/pre-receipts").header("Authorization",manager).param("q","query-nf-")
                .param("warehouseId",warehouse.getId()).param("partnerId",supplier.getId()).param("productId",product.getId()).param("status","PENDENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(21)).andExpect(jsonPath("$.data.content.length()").value(20));
        mvc.perform(get("/api/receipts").header("Authorization",operator).param("q","QUERY-NF-")
                .param("warehouseId",warehouse.getId()).param("page","1").param("size","7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(21)).andExpect(jsonPath("$.data.content.length()").value(7));
        mvc.perform(get("/api/lookups/receipts").header("Authorization",operator).param("q","query-nf-").param("warehouseId",warehouse.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(5)).andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[0].label").isString()).andExpect(jsonPath("$.data.content[0].warehouseId").doesNotExist());
        mvc.perform(get("/api/lookups/pre-receipts").header("Authorization",operator)).andExpect(status().isForbidden());
        var expected=create("1");var received=confirm(expected,"1","QUERY-LPN-"+key(),"LOT-QUERY");
        mvc.perform(get("/api/search/putaway-tasks").header("Authorization",operator).param("q",received.putawayTask().id())
                .param("warehouseId",warehouse.getId()).param("orderId",expected.receipt().id()).param("productId",product.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/putaway-tasks").header("Authorization",operator).param("warehouseId",warehouse.getId())
                .param("lpnId",received.confirmation().lpnId()).param("productId",product.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(received.putawayTask().id()));
    }

    @Test
    void shouldSearchCompletedPutawayProductsFromItsLedgerRatherThanLaterLpnContents() throws Exception {
        var expected=create("1");String firstLpn="HISTORY-A-"+key();
        var first=confirm(expected,"1",firstLpn,"LOT-A");
        postJson("/api/putaway-tasks/"+first.putawayTask().id()+"/start",null,operator,key()).andExpect(status().isOk());
        postJson("/api/putaway-tasks/"+first.putawayTask().id()+"/complete",
                new CompletePutawayRequest(BigDecimal.ONE,firstLpn,storage.getCode()),operator,key()).andExpect(status().isOk());

        var secondProduct=new Product();secondProduct.setCategoryId(product.getCategoryId());secondProduct.setSku("LATER-"+key());
        secondProduct.setName("Produto incluído depois do putaway");secondProduct.setBaseUnit("UN");
        secondProduct.setUnitWeightKg(BigDecimal.ONE);secondProduct.setUnitVolumeM3(new BigDecimal("0.1"));
        secondProduct.setLotControlled(false);secondProduct.setExpiryControlled(false);secondProduct=products.saveAndFlush(secondProduct);
        var secondUnit=new ProductPackaging();secondUnit.setProductId(secondProduct.getId());secondUnit.setType("UN");
        secondUnit.setConversionFactor(BigDecimal.ONE);secondUnit=packaging.saveAndFlush(secondUnit);
        var request=new PreReceiptRequest(warehouse.getId(),supplier.getId(),"LATER-"+key(),null,
                List.of(new ExpectedItem(secondProduct.getId(),BigDecimal.ONE),new ExpectedItem(product.getId(),BigDecimal.ONE)));
        var documentResponse=postJson("/api/pre-receipts",request,manager,key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var secondDocument=data(documentResponse,PreReceiptDetail.class);String secondLpn="HISTORY-B-"+key();
        String secondProductId=secondProduct.getId();
        var confirmation=new ConfirmReceiptRequest(secondDocument.items().stream().filter(item->item.productId().equals(secondProductId)).findFirst().orElseThrow().id(),secondUnit.getId(),BigDecimal.ONE,null,null,
                secondLpn,dock.getId(),secondProduct.getSku(),secondLpn,dock.getCode());
        var receiptResponse=postJson("/api/receipts/"+secondDocument.receipt().id()+"/confirm",confirmation,operator,key())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var second=data(receiptResponse,ReceiptResult.class);
        var additional=new ConfirmReceiptRequest(secondDocument.items().stream().filter(item->item.productId().equals(product.getId())).findFirst().orElseThrow().id(),
                unit.getId(),BigDecimal.ONE,"LOT-A2",expiry(),secondLpn,dock.getId(),product.getSku(),secondLpn,dock.getCode());
        postJson("/api/receipts/"+secondDocument.receipt().id()+"/confirm",additional,operator,key()).andExpect(status().isOk());
        assertNull(tasks.findById(second.putawayTask().id()).orElseThrow().getProductId(),"The cancelled task must represent a mixed-SKU LPN");
        assertNull(tasks.findById(second.putawayTask().id()).orElseThrow().getSourceStockId());
        postJson("/api/tasks/"+second.putawayTask().id()+"/cancel",Map.of("reason","Mover para LPN já armazenado"),manager,key())
                .andExpect(status().isOk());
        var transferResponse=postJson("/api/transfers",Map.of("stockId",second.stockId(),"destinationPositionId",storage.getId(),
                "destinationLpnCode",firstLpn,"quantity",1),operator,key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String transferId=mapper.readTree(transferResponse).path("data").path("id").asText();
        postJson("/api/transfers/"+transferId+"/confirm",Map.of("actualQuantity",1,"scannedLpnCode",secondLpn,
                "scannedOriginCode",dock.getCode(),"scannedDestinationCode",storage.getCode(),"scannedProductCode",secondProduct.getSku()),operator,key())
                .andExpect(status().isOk());

        mvc.perform(get("/api/search/putaway-tasks").header("Authorization",operator).param("q",first.putawayTask().id())
                .param("productId",secondProduct.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/search/putaway-tasks").header("Authorization",operator).param("q",first.putawayTask().id())
                .param("productId",product.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/putaway-tasks").header("Authorization",operator).param("q",second.putawayTask().id())
                .param("productId",secondProduct.getId()).param("status","CANCELADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/putaway-tasks").header("Authorization",operator).param("q",second.putawayTask().id())
                .param("productId",product.getId()).param("status","CANCELADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void shouldConfirmPhysicalReceiptAtomicallyAndReplayTheSameResultWithoutDuplicatingAnything() throws Exception {
        PreReceiptDetail expected=create("10"); String opKey=key();
        ConfirmReceiptRequest request=receipt(expected,"4","LPN-"+key(),"LOTE-A",expiry());
        String first=postJson("/api/receipts/"+expected.receipt().id()+"/confirm",request,operator,opKey)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.putawayTask.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        ReceiptResult result=data(first,ReceiptResult.class);
        String replay=postJson("/api/receipts/"+expected.receipt().id()+"/confirm",request,operator,opKey)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(result,data(replay,ReceiptResult.class));
        assertEquals(1,warehouseStock().size());
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(result.stockId()).orElseThrow().getQuantity()));
        assertEquals(1,confirmations.findByOrderItemId(expected.items().getFirst().id()).size());
        assertEquals(1,tasks.findByOrderId(expected.receipt().id()).size());
        assertEquals(1,ledger.findByOperationIdOrderBySequenceAsc(result.confirmation().operationId()).size());
        assertEquals("EM_RECEBIMENTO",orders.findById(expected.receipt().id()).orElseThrow().getStatus());
        assertTrue(operations.findByIdempotencyKey(opKey).isPresent());
        mvc.perform(get("/api/receipts/{id}",expected.receipt().id()).header("Authorization",operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmations[0].lpnCode").value(request.lpnCode()));
        mvc.perform(get("/api/receipts").param("warehouseId",warehouse.getId()).header("Authorization",operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/putaway-tasks/{id}",result.putawayTask().id()).header("Authorization",operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contents[0].sku").value(product.getSku()));

        ConfirmReceiptRequest altered=receipt(expected,"5",request.lpnCode(),"LOTE-A",expiry());
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",altered,operator,opKey).andExpect(status().isUnprocessableContent());
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(result.stockId()).orElseThrow().getQuantity()));
    }

    @Test
    void shouldRejectMissingTraceDataAndIncorrectScansWithoutCreatingStock() throws Exception {
        PreReceiptDetail expected=create("10"); String lpn="LPN-"+key();
        List<ConfirmReceiptRequest> invalid=List.of(
                receipt(expected,"2",lpn,null,expiry()),receipt(expected,"2",lpn,"LOTE",null),
                new ConfirmReceiptRequest(expected.items().getFirst().id(),unit.getId(),new BigDecimal("2"),"LOTE",expiry(),lpn,dock.getId(),"WRONG",lpn,dock.getCode()),
                new ConfirmReceiptRequest(expected.items().getFirst().id(),unit.getId(),new BigDecimal("2"),"LOTE",expiry(),lpn,dock.getId(),product.getSku(),"WRONG",dock.getCode()),
                new ConfirmReceiptRequest(expected.items().getFirst().id(),unit.getId(),new BigDecimal("2"),"LOTE",expiry(),lpn,dock.getId(),product.getSku(),lpn,"WRONG"));
        for (ConfirmReceiptRequest request:invalid) {
            postJson("/api/receipts/"+expected.receipt().id()+"/confirm",request,operator,key()).andExpect(status().isUnprocessableContent());
        }
        assertTrue(warehouseStock().isEmpty());
        assertTrue(confirmations.findByOrderItemId(expected.items().getFirst().id()).isEmpty());
        assertTrue(lpns.findByTrackingCode(lpn).isEmpty());
        assertTrue(tasks.findByOrderId(expected.receipt().id()).isEmpty());
    }

    @Test
    void shouldRollBackConfirmationLedgerLpnAndTaskWhenDockCapacityIsExceeded() throws Exception {
        PreReceiptDetail expected=create("5");
        dock.setWeightCapacityKg(new BigDecimal("2"));positions.saveAndFlush(dock);
        long ledgerCount=ledger.count();
        String operationKey=key(), lpn="LPN-"+key();
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,"3",lpn,"LOTE",expiry()),operator,operationKey)
                .andExpect(status().isUnprocessableContent());
        assertTrue(warehouseStock().isEmpty());
        assertTrue(ledgerFor(expected).isEmpty());assertEquals(ledgerCount,ledger.count());
        assertTrue(confirmations.findByOrderItemId(expected.items().getFirst().id()).isEmpty());
        assertTrue(lpns.findByTrackingCode(lpn).isEmpty());
        assertTrue(tasks.findByOrderId(expected.receipt().id()).isEmpty());
        assertTrue(operations.findByIdempotencyKey(operationKey).isEmpty());
        assertEquals(0,orderItems.findById(expected.items().getFirst().id()).orElseThrow().getFulfilledQuantity().signum());
    }

    @Test
    void shouldRollBackAReceiptWhenTheWholeLpnCannotFitAnyStoragePosition() throws Exception {
        PreReceiptDetail expected=create("5");storage.setVolumeCapacityM3(new BigDecimal("0.1"));positions.saveAndFlush(storage);
        String lpn="LPN-"+key(), operationKey=key();
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,"2",lpn,"LOTE",expiry()),operator,operationKey)
                .andExpect(status().isUnprocessableContent());
        assertTrue(warehouseStock().isEmpty());assertTrue(ledgerFor(expected).isEmpty());
        assertTrue(confirmations.findByOrderItemId(expected.items().getFirst().id()).isEmpty());
        assertTrue(operations.findByIdempotencyKey(operationKey).isEmpty());
        assertTrue(lpns.findByTrackingCode(lpn).isEmpty());
    }

    @Test
    void shouldMoveEveryLpnLayerOnPutawayAndPreserveLotExpiryEntryDateAndWarehouseQuantity() throws Exception {
        PreReceiptDetail expected=create("6");String lpn="LPN-"+key();
        ReceiptResult first=confirm(expected,"2",lpn,"LOTE-A"), second=confirm(expected,"4",lpn,"LOTE-B");
        assertEquals(first.putawayTask().id(),second.putawayTask().id());
        String taskId=second.putawayTask().id();
        Stock beforeFirst=stocks.findById(first.stockId()).orElseThrow(), beforeSecond=stocks.findById(second.stockId()).orElseThrow();
        Instant firstEntry=beforeFirst.getEntryAt(),secondEntry=beforeSecond.getEntryAt();
        mvc.perform(get("/api/putaway-tasks").header("Authorization",operator)).andExpect(status().isOk());
        postJson("/api/putaway-tasks/"+taskId+"/start",null,operator,key()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EM_EXECUCAO"));
        postJson("/api/putaway-tasks/"+taskId+"/complete",new CompletePutawayRequest(new BigDecimal("6"),lpn,storage.getCode()),operator,key())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONCLUIDA"));
        List<Stock> moved=stocks.findByLpnId(first.confirmation().lpnId());
        assertEquals(2,moved.size());assertTrue(moved.stream().allMatch(s -> storage.getId().equals(s.getPositionId())));
        assertEquals(0,new BigDecimal("6").compareTo(moved.stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertEquals(storage.getId(),lpns.findById(first.confirmation().lpnId()).orElseThrow().getPositionId());
        Stock afterFirst=stocks.findById(first.stockId()).orElseThrow(),afterSecond=stocks.findById(second.stockId()).orElseThrow();
        assertEquals(firstEntry,afterFirst.getEntryAt());assertEquals(secondEntry,afterSecond.getEntryAt());
        assertEquals("LOTE-A",afterFirst.getLot());assertEquals("LOTE-B",afterSecond.getLot());
        assertEquals(expiry(),afterFirst.getExpiryDate());
        assertEquals(List.of("PENDENTE","PENDENTE","EM_EXECUCAO","CONCLUIDA"),history.findByTaskIdOrderByRecordedAtAsc(taskId).stream().map(h -> h.getNewStatus()).toList());
        assertEquals(2,ledger.findByTaskId(taskId).size());
        assertTrue(ledger.findByTaskId(taskId).stream().allMatch(t -> "PUTAWAY".equals(t.getMovementType())));
        assertEquals("ATENDIDO",orders.findById(expected.receipt().id()).orElseThrow().getStatus());
    }

    @Test
    void shouldValidatePutawayStateQuantityAndCodesAndRecheckCapacityAtCompletion() throws Exception {
        PreReceiptDetail expected=create("3");String lpn="LPN-"+key();ReceiptResult received=confirm(expected,"3",lpn,"LOTE");
        String url="/api/putaway-tasks/"+received.putawayTask().id();
        CompletePutawayRequest correct=new CompletePutawayRequest(new BigDecimal("3"),lpn,storage.getCode());
        postJson(url+"/complete",correct,operator,key()).andExpect(status().isUnprocessableContent());
        postJson(url+"/start",null,operator,key()).andExpect(status().isOk());
        for (var invalid:List.of(new CompletePutawayRequest(new BigDecimal("2"),lpn,storage.getCode()),
                new CompletePutawayRequest(new BigDecimal("4"),lpn,storage.getCode()),
                new CompletePutawayRequest(new BigDecimal("3"),"WRONG",storage.getCode()),
                new CompletePutawayRequest(new BigDecimal("3"),lpn,"WRONG"))) {
            postJson(url+"/complete",invalid,operator,key()).andExpect(status().isUnprocessableContent());
        }
        storage.setWeightCapacityKg(BigDecimal.ONE);positions.saveAndFlush(storage);
        postJson(url+"/complete",correct,operator,key()).andExpect(status().isUnprocessableContent());
        assertEquals(dock.getId(),stocks.findById(received.stockId()).orElseThrow().getPositionId());
        assertEquals("EM_EXECUCAO",tasks.findById(received.putawayTask().id()).orElseThrow().getStatus());
        assertTrue(ledger.findByTaskId(received.putawayTask().id()).isEmpty());
        storage.setWeightCapacityKg(new BigDecimal("1000"));positions.saveAndFlush(storage);
        String completeKey=key();
        postJson(url+"/complete",correct,operator,completeKey).andExpect(status().isOk());
        postJson(url+"/complete",correct,operator,completeKey).andExpect(status().isOk());
        postJson(url+"/complete",correct,operator,key()).andExpect(status().isUnprocessableContent());
        assertEquals(1,ledger.findByTaskId(received.putawayTask().id()).size());
    }

    @Test
    void shouldConvertPackagingWithoutRejectingValidSixDecimalQuantities() throws Exception {
        unit.setConversionFactor(new BigDecimal("2.000000"));packaging.saveAndFlush(unit);
        PreReceiptDetail expected=create("4");
        ReceiptResult result=confirm(expected,"2.000000","LPN-"+key(),"LOTE");
        assertEquals(0,new BigDecimal("4").compareTo(result.confirmation().baseQuantity()));
        assertEquals(0,new BigDecimal("4").compareTo(stocks.findById(result.stockId()).orElseThrow().getQuantity()));
    }

    @Test
    void shouldImportInvoiceAsDocumentaryExpectationAndRejectXmlExternalEntities() throws Exception {
        String xml="<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe><ide><nNF>123</nNF></ide><det nItem=\"1\"><prod><cProd>"+product.getSku()+"</cProd><cEAN>"+unit.getBarcode()+"</cEAN><uCom>UN</uCom><qCom>2.000000</qCom></prod></det></infNFe></NFe>";
        String response=postJson("/api/pre-receipts/import",new ImportRequest(warehouse.getId(),supplier.getId(),xml,null),manager,key())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.receipt.documentNumber").value("123"))
                .andReturn().getResponse().getContentAsString();
        PreReceiptDetail imported=data(response,PreReceiptDetail.class);
        assertEquals(0,new BigDecimal("2").compareTo(imported.items().getFirst().requestedQuantity()));
        assertTrue(warehouseStock().isEmpty());
        String unsafe="<?xml version=\"1.0\"?><!DOCTYPE NFe [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><NFe><nNF>&xxe;</nNF></NFe>";
        String unsafeKey=key();
        postJson("/api/pre-receipts/import",new ImportRequest(warehouse.getId(),supplier.getId(),unsafe,null),manager,unsafeKey)
                .andExpect(status().isUnprocessableContent());
        assertTrue(operations.findByIdempotencyKey(unsafeKey).isEmpty());
        assertEquals(1,orders.findAll().stream().filter(o -> warehouse.getId().equals(o.getWarehouseId())).count());
    }

    @Test
    void shouldDenyWrongProfilesAndRejectMissingIdempotencyHeader() throws Exception {
        mvc.perform(post("/api/pre-receipts").header("Authorization",manager).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(document("3","NF"))))
                .andExpect(status().isBadRequest());
        postJson("/api/pre-receipts",document("3","NF"),operator,key()).andExpect(status().isForbidden());
        PreReceiptDetail expected=create("3");
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,"3","LPN-"+key(),"LOTE",expiry()),manager,key())
                .andExpect(status().isForbidden());
        assertTrue(warehouseStock().isEmpty());
    }

    @Test
    void shouldRejectOverReceiptAndEditionAfterPhysicalConfirmation() throws Exception {
        PreReceiptDetail expected=create("3");
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,"4","LPN-"+key(),"LOTE",expiry()),operator,key())
                .andExpect(status().isUnprocessableContent());
        confirm(expected,"1","LPN-"+key(),"LOTE");
        mvc.perform(put("/api/pre-receipts/{id}",expected.receipt().id()).header("Authorization",manager)
                        .header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(document("4","ALTERADA"))))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0,new BigDecimal("3").compareTo(orderItems.findById(expected.items().getFirst().id()).orElseThrow().getRequestedQuantity()));
    }


    @Test
    void shouldAccountForStartedPutawayTasksWhenPlanningAdditionalReceipts() throws Exception {
        storage.setWeightCapacityKg(new BigDecimal("3"));positions.saveAndFlush(storage);
        PreReceiptDetail expected=create("4");
        ReceiptResult first=confirm(expected,"3","LPN-"+key(),"LOTE-A");
        postJson("/api/putaway-tasks/"+first.putawayTask().id()+"/start",null,operator,key()).andExpect(status().isOk());
        postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,"1","LPN-"+key(),"LOTE-B",expiry()),operator,key())
                .andExpect(status().isUnprocessableContent());
        assertEquals(1,warehouseStock().size());
        assertEquals(1,confirmations.findByOrderItemId(expected.items().getFirst().id()).size());
        assertEquals(0,new BigDecimal("3").compareTo(orderItems.findById(expected.items().getFirst().id()).orElseThrow().getFulfilledQuantity()));
    }

    private PreReceiptDetail create(String quantity) throws Exception {
        String response=postJson("/api/pre-receipts",document(quantity,"NF-"+key()),manager,key()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();return data(response,PreReceiptDetail.class);
    }
    private PreReceiptRequest document(String quantity,String number) {
        return new PreReceiptRequest(warehouse.getId(),supplier.getId(),number,null,List.of(new ExpectedItem(product.getId(),new BigDecimal(quantity))));
    }
    private ReceiptResult confirm(PreReceiptDetail expected,String quantity,String lpn,String lot) throws Exception {
        String response=postJson("/api/receipts/"+expected.receipt().id()+"/confirm",receipt(expected,quantity,lpn,lot,expiry()),operator,key())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return data(response,ReceiptResult.class);
    }
    private ConfirmReceiptRequest receipt(PreReceiptDetail expected,String quantity,String lpn,String lot,LocalDate expiry) {
        return new ConfirmReceiptRequest(expected.items().getFirst().id(),unit.getId(),new BigDecimal(quantity),lot,expiry,lpn,dock.getId(),product.getSku(),lpn,dock.getCode());
    }
    private ResultActions postJson(String url,Object request,String token,String key) throws Exception {
        var post=post(url).header("Authorization",token).header("Idempotency-Key",key);
        if (request!=null) post.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request));
        return mvc.perform(post);
    }
    private <T>T data(String json,Class<T> type) { return mapper.readValue(mapper.readTree(json).path("data").toString(),type); }
    private static String key() { return UUID.randomUUID().toString(); }
    private static LocalDate expiry() { return LocalDate.of(2030,12,31); }
    private List<Stock> warehouseStock() { return stocks.findByPositionIdIn(List.of(dock.getId(),storage.getId())); }
    private List<dev.lourencogabriel.wmssaas.audit.model.StockTransaction> ledgerFor(PreReceiptDetail expected) {
        return ledger.findAll().stream().filter(t -> warehouseStock().stream().anyMatch(s -> s.getId().equals(t.getStockId()))).toList();
    }
    private Position position(Zone zone,String code,String purpose) {
        Position p=new Position();p.setZoneId(zone.getId());p.setCode(code);p.setType("FIXA");p.setPurpose(purpose);p.setStatus("LIVRE");
        p.setWeightCapacityKg(new BigDecimal("1000"));p.setVolumeCapacityM3(new BigDecimal("1000"));return positions.saveAndFlush(p);
    }
    private String token(String profile,String email) {
        User user=new User("Inbound "+profile,email,"unused-test-hash");user.setProfile(profiles.findById(profile).orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE);user.setEmailVerifiedAt(Instant.now());user=users.saveAndFlush(user);
        return "Bearer "+tokens.generateTokens(user.getUlid()).token();
    }
}
