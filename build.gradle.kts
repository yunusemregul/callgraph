plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "callgraph"
version = "1.5"

repositories {
    mavenCentral()
}

intellij {
    version = "2022.1"
    type = "IC"
    plugins.set(listOf("com.intellij.java"))
}

dependencies {
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "8"
        targetCompatibility = "8"
    }

    val buildFrontend by registering(Exec::class) {
        workingDir("src/main/frontend")
        commandLine("npm", "run", "build:prod")
    }

    processResources {
        dependsOn(buildFrontend)
    }

    runIde {
        // Get VM options from Gradle property if set
        val ideaVmOptions = project.findProperty("ideaVmOptions") as String?
        if (ideaVmOptions != null) {
            jvmArgs(ideaVmOptions.split(" "))
        }
    }

    patchPluginXml {
        sinceBuild = "211" // Support from 2021.1
        untilBuild = "" // Empty string means no upper limit (support all future versions)
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
