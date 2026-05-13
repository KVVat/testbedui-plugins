pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "testbedui-plugins"

include(":common-utils")
include(":test-sample")
include(":apps:assets-target-app")
include(":apps:uniqueid")
include(":apps:openurl")
include(":apps:directboot")
include(":apps:appupdate")
include(":apps:encryption")
include(":apps:assets-attacker-app")
include(":apps:openurl-niapsec")
