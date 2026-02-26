pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        gradlePluginPortal()
    }
}

plugins {
    // Foojay toolchain resolver — lets Gradle auto-download JDK 21 for the daemon
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
