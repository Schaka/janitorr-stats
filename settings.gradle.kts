pluginManagement {
    val quarkusPluginVersion: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("io.quarkus") version quarkusPluginVersion
        id("io.quarkus.extension") version quarkusPluginVersion
    }
}

rootProject.name = "janitorr-stats"

include(":local-devservices", ":local-devservices-deployment")
