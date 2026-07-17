package fixture.mixed

object Notifier {
    fun notifyCustomer() {
        AuditSink.record()
    }
}
