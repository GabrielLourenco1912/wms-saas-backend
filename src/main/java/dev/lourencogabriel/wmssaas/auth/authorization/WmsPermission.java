package dev.lourencogabriel.wmssaas.auth.authorization;

/** Explicit actions derived from ARTIGO_WMS, RN001/RN012 and RF001–RF017. */
public enum WmsPermission {
    WAREHOUSE_VIEW("RF003"), WAREHOUSE_CREATE("RF003"), WAREHOUSE_EDIT("RF003"),
    PRODUCT_VIEW("RF004"), PRODUCT_CREATE("RF004"), PRODUCT_EDIT("RF004"),
    PRE_RECEIPT_VIEW("RF005A"), PRE_RECEIPT_CREATE("RF005A"), PRE_RECEIPT_EDIT("RF005A"),
    RECEIPT_VIEW("RF005B"), RECEIPT_EXECUTE("RF005B"),
    PUTAWAY_VIEW("RF007"), PUTAWAY_EXECUTE("RF007"),
    ORDER_VIEW("RF008"), ORDER_CREATE("RF008"), ORDER_EDIT("RF008"), ORDER_CANCEL("RF015"),
    WAVE_VIEW("RF008"), WAVE_CREATE("RF008"), WAVE_CANCEL("RF015"),
    PICKING_VIEW("RF009"), PICKING_EXECUTE("RF009"), PICKING_REPORT_DIVERGENCE("RF010"),
    TRANSFER_VIEW("RF011"), TRANSFER_EXECUTE("RF011"),
    STOCK_VIEW("RF014"), STOCK_BLOCK("RF012"), STOCK_UNBLOCK("RF012"), AUDIT_VIEW("RF014"),
    SHIPMENT_VIEW("RF013"), SHIPMENT_EXECUTE("RF013"),
    INVENTORY_VIEW("RF017"), INVENTORY_EXECUTE("RF017"), INVENTORY_ADJUST("RF017"),
    TASK_VIEW("RF006"), TASK_CANCEL("RF015"), TASK_QUANTITY_OVERRIDE("RF009"),
    USER_VIEW("RF016"), USER_CREATE("RF016"), USER_EDIT("RF016"), USER_PROFILE_ASSIGN("RF016"),
    PROFILE_VIEW("RF016"), PROFILE_EDIT("RF016");

    private final String functionalRequirement;

    WmsPermission(String functionalRequirement) {
        this.functionalRequirement = functionalRequirement;
    }

    public String functionalRequirement() {
        return this.functionalRequirement;
    }

    public String authority() {
        return "WMS_" + this.name();
    }
}
