package fixture.java;

public final class PaymentService {
    private PaymentService() {
    }

    public static void charge() {
        ReceiptService.create();
    }
}
