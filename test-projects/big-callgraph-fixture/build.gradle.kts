plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

sourceSets {
    main {
        java.srcDir("src/generated/main/java")
    }
    test {
        java.srcDir("src/generated/test/java")
    }
}

val generateFixture by tasks.registering(Exec::class) {
    workingDir(projectDir)
    commandLine("node", "generate-fixture.cjs")
}

tasks.named("compileJava") {
    dependsOn(generateFixture)
}

tasks.named("compileTestJava") {
    dependsOn(generateFixture)
}
