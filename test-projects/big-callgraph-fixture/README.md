# Big call graph fixture

This fixture generates a deterministic stress project instead of tracking hundreds of repetitive
Java files.

## Generate and compile

From the CallGraph repository root:

```bash
./gradlew -p test-projects/big-callgraph-fixture clean test
```

This creates 266 production classes and 15 test classes under `src/generated`.
The root plugin project's `./gradlew test` command also regenerates this fixture and runs an
IntelliJ PSI stress test against it.

## Launch in the plugin sandbox

```bash
./gradlew runIde --args="$PWD/test-projects/big-callgraph-fixture"
```

Wait for Gradle import and indexing to finish before generating a graph.

## Large callee graph

Open `fixture.big.BigGraphEntry` and place the caret inside `start()`.

The full topology contains:

- 230 reachable nodes: the root, 19 layers × 12 nodes, and one sink
- 672 call edges
- three outgoing edges per intermediate layer node
- many converging paths into shared downstream nodes

To load the complete graph, use:

- Lazy Mode: off
- Direction: Callees
- Max Depth: 20
- Max Total Nodes: at least 300
- Physics: off initially

With the default Max Depth of 8, only the root and first eight layers are traversed: 97 nodes.
With Max Total Nodes set to 150, generation must stop at the configured safety limit.

## Lazy expansion

Enable Lazy Mode and regenerate from `BigGraphEntry.start`. The first **+ callees** expansion adds
12 nodes. Every layer-zero node should retain its own **+ callees** action, allowing individual
branches to be explored without creating the full graph.

## Caller batching

Open `fixture.big.fanin.Hotspot` and place the caret inside `execute()`.

Use:

- Lazy Mode: on
- Max Callers Per Node: 10
- Filter Out Tests: on

There are 35 production callers. The caller satellite should load them in batches of 10, 10, 10,
and 5, remaining as **+ more callers** until the final batch. Another 15 callers live under the test
source root and must stay hidden while test filtering is enabled.
