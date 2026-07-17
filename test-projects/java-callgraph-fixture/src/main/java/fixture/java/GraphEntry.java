package fixture.java;

public final class GraphEntry {
    private GraphEntry() {
    }

    public static void start() {
        OrderService.placeOrder();
        AuditService.record();
    }
}
