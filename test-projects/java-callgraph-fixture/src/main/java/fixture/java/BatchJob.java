package fixture.java;

public final class BatchJob {
    private BatchJob() {
    }

    public static void run() {
        OrderService.placeOrder();
    }
}
