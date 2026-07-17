package fixture.mixed

class DiscountPolicy {
    fun apply() {
        AuditSink.record()
    }
}
