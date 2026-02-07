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
include(":apps:target-test-app")
