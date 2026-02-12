pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
