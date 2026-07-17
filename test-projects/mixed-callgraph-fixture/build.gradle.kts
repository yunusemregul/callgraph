plugins {
    java
    kotlin("jvm") version "1.9.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}
