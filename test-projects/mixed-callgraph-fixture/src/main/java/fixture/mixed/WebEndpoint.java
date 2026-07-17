package fixture.mixed;

public final class WebEndpoint {
    public void handle() {
        new CheckoutFlow().checkout();
    }
}
