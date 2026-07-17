package fixture.java;

public final class InventoryService {
    private InventoryService() {
    }

    public static void reserve() {
        AuditService.record();
    }
}
