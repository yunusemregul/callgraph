# IntelliJ integration fixtures

These projects are intentionally small call graphs for exercising the plugin in a real IntelliJ sandbox.
They are not included in the plugin's Gradle build.

## Verify everything

From the repository root, run:

```bash
./verify-all
```

This compiles all standalone fixtures, runs the frontend behavior tests and IntelliJ PSI graph
tests, and prints a grouped report with a checkmark for every passing scenario.

The same fixture sources are loaded by IntelliJ Platform integration tests in
`CallGraphGeneratorIntegrationTest`. Running `./gradlew test` resolves the fixture methods through
IntelliJ PSI and asserts exact graph nodes and edges, recursive Lazy Mode expansion, test filtering,
and Java/Kotlin interop.

## Launch

From the repository root:

```bash
./gradlew runIde --args="$PWD/test-projects/java-callgraph-fixture"
./gradlew runIde --args="$PWD/test-projects/mixed-callgraph-fixture"
```

Wait for indexing to finish, put the caret inside the documented root method, and invoke
**Generate Call Graph** with Option+Shift+E on macOS or Alt+Shift+E elsewhere.

## Java fixture

Root method: `fixture.java.GraphEntry.start`

With Direction set to **Callees** and test filtering enabled, the complete graph is:

```text
GraphEntry.start
├── OrderService.placeOrder
│   ├── PaymentService.charge
│   │   └── ReceiptService.create
│   └── InventoryService.reserve
│       └── AuditService.record
└── AuditService.record
```

Expected: 6 nodes and 6 edges. `System.out.println` is excluded as a library call.

For `OrderService.placeOrder` in caller direction, the production callers are
`GraphEntry.start` and `BatchJob.run`. `OrderServiceTest.callsPlaceOrder` must be excluded
when **Filter Out Tests** is enabled.

In Lazy Mode, expanding `GraphEntry.start` callees must reveal `OrderService.placeOrder`
and `AuditService.record`. `OrderService.placeOrder` must itself retain a **+ callees**
action, revealing `PaymentService.charge` and `InventoryService.reserve`.

## Mixed Java/Kotlin fixture

Root method: `fixture.mixed.CheckoutFlow.checkout`

The important cross-language paths are:

```text
WebEndpoint.handle [Java]
└── CheckoutFlow.checkout [Kotlin]
    ├── PricingService.calculate [Java]
    │   ├── DiscountPolicy.apply [Kotlin]
    │   │   └── AuditSink.record [Java]
    │   └── TaxService.addTax [Java]
    └── Notifier.notifyCustomer [Kotlin]
        └── AuditSink.record [Java]
```

`BatchCheckout.run` is a second Kotlin caller of `CheckoutFlow.checkout`.

## Big fixture

`big-callgraph-fixture` is a generated stress project containing 266 production classes and 15 test
classes. Its main graph has 230 reachable nodes and 672 edges, while a separate hotspot has 35
production callers for exercising caller batching. See its local `README.md` for generation,
launch, and expected-result instructions.
