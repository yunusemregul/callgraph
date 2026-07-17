package fixture.mixed;

public final class PricingService {
    private PricingService() {
    }

    public static void calculate() {
        new DiscountPolicy().apply();
        TaxService.addTax();
    }
}
