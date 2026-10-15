package dev.lourencogabriel.wmssaas.inbound.service;

import dev.lourencogabriel.wmssaas.inbound.model.ReceiptConfirmation;
import dev.lourencogabriel.wmssaas.inbound.repository.ReceiptConfirmationRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.model.ProductPackaging;
import dev.lourencogabriel.wmssaas.product.repository.ProductPackagingRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import org.xml.sax.InputSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;
import static dev.lourencogabriel.wmssaas.operation.service.OperationService.currentActorId;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class InboundService {
    private final WarehouseOrderRepository orders;
    private final OrderItemRepository items;
    private final ReceiptConfirmationRepository confirmations;
    private final ProductRepository products;
    private final ProductPackagingRepository packaging;
    private final PartnerRepository partners;
    private final PositionRepository positions;
    private final LpnRepository lpns;
    private final StockRepository stocks;
    private final WarehouseTaskRepository tasks;
    private final StockMutationService mutation;
    private final OperationService operations;
    private final EntityManager entityManager;

    @Transactional(readOnly=true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_VIEW')")
    public PageResponse<WarehouseOrder> preReceipts(String warehouseId, String status, Pageable pageable) {
        return list(warehouseId,status,pageable);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'RECEIPT_VIEW')")
    public PageResponse<WarehouseOrder> receipts(String warehouseId, String status, Pageable pageable) {
        return list(warehouseId,status,pageable);
    }
    private PageResponse<WarehouseOrder> list(String warehouseId,String status,Pageable pageable) {
        Specification<WarehouseOrder> spec = (r,q,b) -> b.equal(r.get("type"),"ENTRADA");
        if (warehouseId != null) spec = spec.and((r,q,b) -> b.equal(r.get("warehouseId"),warehouseId));
        if (status != null) spec = spec.and((r,q,b) -> b.equal(r.get("status"),status));
        return PageResponse.from(orders.findAll(spec,pageable));
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_VIEW') or @wmsAuthorization.can(authentication, 'RECEIPT_VIEW')")
    public PreReceiptDetail detail(String id) {
        WarehouseOrder order = order(id);
        List<OrderItem> orderItems = items.findByOrderId(id);
        List<ReceiptConfirmation> received = orderItems.stream().flatMap(i -> confirmations.findByOrderItemId(i.getId()).stream()).toList();
        return new PreReceiptDetail(order,orderItems,received);
    }
    private WarehouseOrder order(String id) {
        return orders.findById(id).filter(o -> "ENTRADA".equals(o.getType())).orElseThrow(() -> new ResourceNotFoundException("Pre-receipt",id));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_CREATE')")
    public PreReceiptDetail create(PreReceiptRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,request,actor,"PRE_RECEBIMENTO",PreReceiptDetail.class,op -> createDocument(request,actor));
    }
    private PreReceiptDetail createDocument(PreReceiptRequest request,String actor) {
        mutation.lockWarehouse(request.warehouseId()); validateDocument(request);
        WarehouseOrder order = new WarehouseOrder(); order.setWarehouseId(request.warehouseId()); order.setPartnerId(request.partnerId());
        order.setDocumentNumber(request.documentNumber().trim()); order.setExpectedAt(request.expectedAt()); order.setType("ENTRADA");
        order.setStatus("PENDENTE"); order.setCreatedById(actor); order.setCreatedAt(Instant.now()); order.setUpdatedAt(Instant.now()); orders.saveAndFlush(order);
        List<OrderItem> lines = writeItems(order.getId(),request.items());
        // RN017: documentary expectation deliberately creates neither stock nor a stock transaction.
        return new PreReceiptDetail(order,lines,List.of());
    }
    private void validateDocument(PreReceiptRequest request) {
        Partner partner = partners.findById(request.partnerId()).filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> new BusinessRuleException("An active supplier is required"));
        if (!Set.of("FORNECEDOR","AMBOS").contains(partner.getType())) throw new BusinessRuleException("Partner is not a supplier");
        if (request.items() == null || request.items().isEmpty()) throw new BusinessRuleException("At least one expected item is required");
        Set<String> productIds = new HashSet<>();
        for (ExpectedItem item : request.items()) {
            StockMutationService.positive(item.quantity());
            if (!productIds.add(item.productId())) throw new BusinessRuleException("A product can appear only once; aggregate its quantities");
            products.findById(item.productId()).filter(p -> Boolean.TRUE.equals(p.getActive())).orElseThrow(() -> new BusinessRuleException("Expected product is inactive or unavailable"));
        }
    }
    private List<OrderItem> writeItems(String orderId,List<ExpectedItem> expected) {
        return expected.stream().map(line -> {
            OrderItem item = new OrderItem(); item.setOrderId(orderId); item.setProductId(line.productId()); item.setRequestedQuantity(line.quantity());
            item.setFulfilledQuantity(BigDecimal.ZERO); item.setPickedQuantity(BigDecimal.ZERO); return items.save(item);
        }).toList();
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_EDIT')")
    public PreReceiptDetail update(String id,PreReceiptRequest request,String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"EDITAR_PRE_RECEBIMENTO",PreReceiptDetail.class,op -> {
            WarehouseOrder order = order(id); mutation.lockWarehouse(order.getWarehouseId()); entityManager.refresh(order,LockModeType.PESSIMISTIC_WRITE);
            if (!order.getWarehouseId().equals(request.warehouseId())) throw new BusinessRuleException("The warehouse cannot be changed");
            List<OrderItem> current = items.findByOrderId(id);
            if (!"PENDENTE".equals(order.getStatus()) || current.stream().anyMatch(i -> !confirmations.findByOrderItemId(i.getId()).isEmpty())) throw new BusinessRuleException("A receipt already being checked cannot be edited");
            validateDocument(request); order.setPartnerId(request.partnerId()); order.setDocumentNumber(request.documentNumber()); order.setExpectedAt(request.expectedAt());
            items.deleteAll(current); items.flush(); List<OrderItem> lines = writeItems(id,request.items()); orders.save(order);
            return new PreReceiptDetail(order,lines,List.of());
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_CREATE')")
    public PreReceiptDetail importInvoice(ImportRequest request,String key) {
        String actor = currentActorId();
        return operations.execute(key,request,actor,"IMPORTAR_NFE",PreReceiptDetail.class,op -> createDocument(parseInvoice(request),actor));
    }
    /** Reads supplier/document/items from NF-e without trusting XML entities or executing fiscal processing. */
    private PreReceiptRequest parseInvoice(ImportRequest request) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(request.invoiceXml())));
            String number = text(doc.getDocumentElement(),"nNF");
            if (number == null || number.isBlank()) throw new BusinessRuleException("NF-e document number is missing");
            NodeList lines = doc.getElementsByTagNameNS("*","det");
            if (lines.getLength() == 0 || lines.getLength() > 500) throw new BusinessRuleException("NF-e must contain between 1 and 500 items");
            Map<String,BigDecimal> expected = new LinkedHashMap<>();
            for (int i=0;i<lines.getLength();i++) {
                Element line = (Element) lines.item(i); String sku = text(line,"cProd"), barcode = text(line,"cEAN");
                BigDecimal quantity = new BigDecimal(text(line,"qCom")); StockMutationService.positive(quantity);
                ProductPackaging pack = barcode == null ? null : packaging.findByBarcode(barcode).orElse(null);
                Product product;
                if (pack != null && Boolean.TRUE.equals(pack.getActive())) {
                    product = mutation.product(pack.getProductId()); quantity = quantity.multiply(pack.getConversionFactor());
                } else product = products.findBySku(sku).orElseThrow(() -> new BusinessRuleException("NF-e SKU is not registered: " + sku));
                StockMutationService.positive(quantity); expected.merge(product.getId(),quantity,BigDecimal::add);
            }
            return new PreReceiptRequest(request.warehouseId(),request.partnerId(),number,request.expectedAt(),expected.entrySet().stream().map(e -> new ExpectedItem(e.getKey(),e.getValue())).toList());
        } catch (BusinessRuleException e) { throw e; }
        catch (Exception e) { throw new BusinessRuleException("NF-e XML is invalid or contains unsupported fields"); }
    }
    private String text(Element element,String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*",localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'RECEIPT_EXECUTE')")
    public ReceiptResult confirm(String id,ConfirmReceiptRequest request,String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"CONFERIR_RECEBIMENTO",ReceiptResult.class,op -> {
            WarehouseOrder order = order(id); mutation.lockWarehouse(order.getWarehouseId()); entityManager.refresh(order,LockModeType.PESSIMISTIC_WRITE);
            if (!Set.of("PENDENTE","EM_RECEBIMENTO").contains(order.getStatus())) throw new BusinessRuleException("Receipt is no longer pending");
            OrderItem item = items.findByIdForUpdate(request.orderItemId()).filter(i -> id.equals(i.getOrderId())).orElseThrow(() -> new ResourceNotFoundException("Receipt item",request.orderItemId()));
            Product product = mutation.lockProduct(item.getProductId());
            if (!Boolean.TRUE.equals(product.getActive())) throw new BusinessRuleException("Product is inactive");
            ProductPackaging pack = packaging.findById(request.packagingId()).filter(p -> product.getId().equals(p.getProductId()) && Boolean.TRUE.equals(p.getActive()))
                    .orElseThrow(() -> new BusinessRuleException("An active packaging of this product is required"));
            StockMutationService.positive(request.quantity()); BigDecimal baseQuantity = request.quantity().multiply(pack.getConversionFactor()); StockMutationService.positive(baseQuantity);
            if (item.getFulfilledQuantity().add(baseQuantity).compareTo(item.getRequestedQuantity()) > 0) throw new BusinessRuleException("Receipt quantity exceeds the expected item");
            mutation.validateTrace(product,request.lot(),request.expiryDate()); mutation.lockPositions(List.of(request.positionId()));
            Position dock = mutation.position(request.positionId());
            if (!"RECEBIMENTO".equals(dock.getPurpose()) || !order.getWarehouseId().equals(mutation.warehouseForPosition(dock.getId()))) throw new BusinessRuleException("Receipt requires an inbound dock in the order warehouse");
            String expectedCode = pack.getBarcode() == null || pack.getBarcode().isBlank() ? product.getSku() : pack.getBarcode();
            if (!request.scannedProductCode().equals(product.getSku())) StockMutationService.requireCode(request.scannedProductCode(),expectedCode,"SKU or packaging");
            StockMutationService.requireCode(request.scannedPositionCode(),dock.getCode(),"receiving dock");
            StockMutationService.requireCode(request.scannedLpnCode(),request.lpnCode(),"LPN");
            Lpn lpn = mutation.getOrCreateLpn(request.lpnCode(),dock);
            List<WarehouseTask> currentTasks = tasks.findByLpnId(lpn.getId()).stream().filter(t -> "PUTAWAY".equals(t.getType()) && Set.of("PENDENTE","EM_EXECUCAO").contains(t.getStatus())).toList();
            if (currentTasks.stream().anyMatch(t -> "EM_EXECUCAO".equals(t.getStatus()))) throw new BusinessRuleException("LPN putaway has already started");
            ReceiptConfirmation confirmation = new ReceiptConfirmation(); confirmation.setOrderItemId(item.getId()); confirmation.setPackagingId(pack.getId());
            confirmation.setLpnId(lpn.getId()); confirmation.setPositionId(dock.getId()); confirmation.setInformedQuantity(request.quantity()); confirmation.setBaseQuantity(baseQuantity);
            confirmation.setLot(request.lot()); confirmation.setExpiryDate(request.expiryDate()); confirmation.setUserId(actor); confirmation.setOperationId(op); confirmation.setConfirmedAt(Instant.now());
            confirmations.saveAndFlush(confirmation);
            Stock stock = mutation.receive(product,lpn,dock,baseQuantity,request.lot(),request.expiryDate(),confirmation.getId(),actor,op);
            item.setFulfilledQuantity(item.getFulfilledQuantity().add(baseQuantity)); items.save(item);
            boolean complete = items.findByOrderId(id).stream().allMatch(i -> i.getRequestedQuantity().compareTo(i.getFulfilledQuantity()) == 0);
            order.setStatus(complete ? "ATENDIDO" : "EM_RECEBIMENTO"); orders.save(order);
            WarehouseTask task = currentTasks.isEmpty() ? new WarehouseTask() : currentTasks.getFirst();
            boolean created = task.getId() == null;
            task.setType("PUTAWAY"); task.setLpnId(lpn.getId()); task.setOriginPositionId(dock.getId());
            task.setOrderId(id); task.setStatus("PENDENTE"); task.setPriority(1);
            List<Stock> lpnStocks = stocks.findByLpnId(lpn.getId());
            task.setTargetQuantity(lpnStocks.stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add));
            task.setProductId(lpnStocks.stream().map(Stock::getProductId).distinct().count() == 1 ? product.getId() : null);
            task.setSourceStockId(lpnStocks.size() == 1 ? stock.getId() : null);
            task.setDestinationPositionId(putawayDestination(order.getWarehouseId(),lpnStocks,task.getId()));
            if (created) task.setCreatedAt(Instant.now()); tasks.saveAndFlush(task);
            mutation.history(task,created ? null : "PENDENTE",actor,op,created ? "Putaway generated by receipt" : "Additional LPN content included in putaway");
            return new ReceiptResult(confirmation,stock.getId(),task);
        });
    }
    private String putawayDestination(String warehouseId,List<Stock> incoming,String excludingTaskId) {
        BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO;
        for (Stock s : incoming) { Product p=mutation.product(s.getProductId()); weight=weight.add(s.getQuantity().multiply(p.getUnitWeightKg())); volume=volume.add(s.getQuantity().multiply(p.getUnitVolumeM3())); }
        for (Position position : positions.findByWarehouseId(warehouseId)) {
            if (!"ARMAZENAGEM".equals(position.getPurpose())) continue;
            try {
                mutation.ensureDestination(position);
                BigDecimal plannedWeight=weight, plannedVolume=volume;
                for (WarehouseTask task : tasks.findByStatus("PENDENTE")) {
                    if (!"PUTAWAY".equals(task.getType()) || !position.getId().equals(task.getDestinationPositionId()) || Objects.equals(task.getId(),excludingTaskId)) continue;
                    for (Stock balance : stocks.findByLpnId(task.getLpnId())) {
                        Product p=mutation.product(balance.getProductId()); plannedWeight=plannedWeight.add(balance.getQuantity().multiply(p.getUnitWeightKg())); plannedVolume=plannedVolume.add(balance.getQuantity().multiply(p.getUnitVolumeM3()));
                    }
                }
                mutation.requireCapacity(position,plannedWeight,plannedVolume); return position.getId();
            } catch (BusinessRuleException unavailable) { /* Continue searching another compatible position. */ }
        }
        throw new BusinessRuleException("No storage position has enough available capacity for the LPN");
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_VIEW')")
    public PageResponse<WarehouseTask> putawayTasks(Pageable pageable) { return PageResponse.from(tasks.findByType("PUTAWAY",pageable)); }
    @Transactional(readOnly=true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_VIEW')")
    public WarehouseTask putawayTask(String id) {
        return tasks.findById(id).filter(t -> "PUTAWAY".equals(t.getType())).orElseThrow(() -> new ResourceNotFoundException("Putaway task",id));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_EXECUTE')")
    public WarehouseTask startPutaway(String id,String key) {
        String actor=currentActorId();
        return operations.execute(key,List.of(id,"start"),actor,"INICIAR_PUTAWAY",WarehouseTask.class,op -> {
            WarehouseTask task=putawayWithoutAuth(id); mutation.lockWarehouse(mutation.warehouseForPosition(task.getOriginPositionId())); entityManager.refresh(task,LockModeType.PESSIMISTIC_WRITE);
            mutation.transition(task,"EM_EXECUCAO",actor,op,null); return tasks.save(task);
        });
    }
    private WarehouseTask putawayWithoutAuth(String id) {
        return tasks.findById(id).filter(t -> "PUTAWAY".equals(t.getType())).orElseThrow(() -> new ResourceNotFoundException("Putaway task",id));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_EXECUTE')")
    public WarehouseTask completePutaway(String id,CompletePutawayRequest request,String key) {
        String actor=currentActorId();
        return operations.execute(key,List.of(id,request),actor,"CONCLUIR_PUTAWAY",WarehouseTask.class,op -> {
            WarehouseTask task=putawayWithoutAuth(id); mutation.lockWarehouse(mutation.warehouseForPosition(task.getOriginPositionId())); entityManager.refresh(task,LockModeType.PESSIMISTIC_WRITE);
            if (!"EM_EXECUCAO".equals(task.getStatus()) || !actor.equals(task.getAssignedUserId())) throw new BusinessRuleException("Putaway must be started by the current operator");
            mutation.lockPositions(List.of(task.getOriginPositionId(),task.getDestinationPositionId()));
            Lpn lpn=lpns.findByIdForUpdate(task.getLpnId()).orElseThrow(); Position destination=mutation.position(task.getDestinationPositionId());
            StockMutationService.requireCode(request.scannedLpnCode(),lpn.getTrackingCode(),"LPN");
            StockMutationService.requireCode(request.scannedDestinationCode(),destination.getCode(),"destination position");
            StockMutationService.positive(request.actualQuantity());
            BigDecimal current=stocks.findByLpnId(lpn.getId()).stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            if (request.actualQuantity().compareTo(task.getTargetQuantity()) != 0 || current.compareTo(request.actualQuantity()) != 0) throw new BusinessRuleException("Putaway quantity must match the complete LPN");
            if (!Objects.equals(lpn.getPositionId(),task.getOriginPositionId())) throw new BusinessRuleException("LPN has moved since task generation");
            mutation.moveWholeLpn(lpn,destination,actor,op,id,"PUTAWAY"); task.setActualQuantity(request.actualQuantity());
            mutation.transition(task,"CONCLUIDA",actor,op,null); return tasks.save(task);
        });
    }
}
