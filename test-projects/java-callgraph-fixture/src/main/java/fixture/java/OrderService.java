package fixture.java;

public final class OrderService {
    private OrderService() {
    }

    public static void placeOrder() {
        PaymentService.charge();
        InventoryService.reserve();
    }
}
