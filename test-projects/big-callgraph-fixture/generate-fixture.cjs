const fs = require('node:fs')
const path = require('node:path')

const layers = 19
const width = 12
const fanout = 3
const productionCallers = 35
const testCallers = 15

const generatedRoot = path.join(__dirname, 'src', 'generated')
const mainRoot = path.join(generatedRoot, 'main', 'java')
const testRoot = path.join(generatedRoot, 'test', 'java')

fs.rmSync(generatedRoot, { recursive: true, force: true })

function padded(value) {
    return String(value).padStart(2, '0')
}

function writeJava(root, packageName, className, body) {
    const packagePath = packageName.replaceAll('.', path.sep)
    const directory = path.join(root, packagePath)
    fs.mkdirSync(directory, { recursive: true })
    fs.writeFileSync(path.join(directory, className + '.java'),
        `package ${packageName};\n\n${body}\n`)
}

const rootCalls = Array.from({ length: width }, (_, index) =>
    `        fixture.big.layer00.Node${padded(index)}.run();`).join('\n')

writeJava(mainRoot, 'fixture.big', 'BigGraphEntry', `public final class BigGraphEntry {
    private BigGraphEntry() {
    }

    public static void start() {
${rootCalls}
    }
}`)

writeJava(mainRoot, 'fixture.big', 'Sink', `public final class Sink {
    private Sink() {
    }

    public static void finish() {
    }
}`)

for (let layer = 0; layer < layers; layer++) {
    const packageName = `fixture.big.layer${padded(layer)}`
    for (let index = 0; index < width; index++) {
        const className = `Node${padded(index)}`
        let calls

        if (layer === layers - 1) {
            calls = '        fixture.big.Sink.finish();'
        } else {
            const targets = [index, (index + 1) % width, (index + 5) % width]
            calls = targets.slice(0, fanout).map(target =>
                `        fixture.big.layer${padded(layer + 1)}.Node${padded(target)}.run();`
            ).join('\n')
        }

        writeJava(mainRoot, packageName, className, `public final class ${className} {
    private ${className}() {
    }

    public static void run() {
${calls}
    }
}`)
    }
}

writeJava(mainRoot, 'fixture.big.fanin', 'Hotspot', `public final class Hotspot {
    private Hotspot() {
    }

    public static void execute() {
        fixture.big.Sink.finish();
    }
}`)

for (let index = 0; index < productionCallers; index++) {
    const className = `HotspotCaller${padded(index)}`
    writeJava(mainRoot, 'fixture.big.fanin', className, `public final class ${className} {
    private ${className}() {
    }

    public static void invoke() {
        Hotspot.execute();
    }
}`)
}

for (let index = 0; index < testCallers; index++) {
    const className = `HotspotCaller${padded(index)}Test`
    writeJava(testRoot, 'fixture.big.fanin', className, `public final class ${className} {
    public void invoke() {
        Hotspot.execute();
    }
}`)
}

const mainClasses = 2 + (layers * width) + 1 + productionCallers
const testClasses = testCallers
const calleeNodes = 2 + (layers * width)
const calleeEdges = width + ((layers - 1) * width * fanout) + width

console.log(`Generated ${mainClasses} main classes and ${testClasses} test classes.`)
console.log(`BigGraphEntry.start has ${calleeNodes} reachable nodes and ${calleeEdges} call edges.`)
console.log(`Hotspot.execute has ${productionCallers} production callers and ${testCallers} test callers.`)
