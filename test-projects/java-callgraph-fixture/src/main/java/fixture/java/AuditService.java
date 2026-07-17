package fixture.java;

public final class AuditService {
    private AuditService() {
    }

    public static void record() {
        System.out.println("audit");
    }
}
