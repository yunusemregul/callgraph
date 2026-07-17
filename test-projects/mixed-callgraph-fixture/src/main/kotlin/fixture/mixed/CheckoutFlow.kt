package fixture.mixed

class CheckoutFlow {
    fun checkout() {
        PricingService.calculate()
        Notifier.notifyCustomer()
    }
}
