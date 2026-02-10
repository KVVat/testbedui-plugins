# TestBed UI Plugins

This repository contains custom test plugins and target Android applications designed to work with the **TestBed Core** ecosystem.

## Project Structure

* **:common-utils**: Shared utility classes and extensions used by multiple plugins.
* **:test-sample**: A sample JUnit-based test plugin for Android device automation.
* **:apps:target-test-app**: A target Android application used for verifying test scenarios, featuring Room database integration.

## Getting Started

### Prerequisites

* **JDK 17** is required for both JVM plugins and Android app builds.
* **Project Layout**: The **TestBed Core** repository must be located at `../testbedui` relative to this project root to enable automatic plugin deployment paths.

### 1. Building and Deploying Plugins

To compile a plugin into a JAR file and automatically deploy it to the TestBed Core's plugin directory, run the specific project's `jar` task. For the sample plugin:

`./gradlew :test-sample:jar`

The build script is configured to automatically place the generated JAR at:
`../testbedui/composeApp/plugins/test-sample/test-sample.jar`

### 2. Building the Target Android App

To build the sample Android application for testing on a device:

`./gradlew :apps:target-test-app:assembleDebug`

The output APK will be located at:
`apps/target-test-app/build/outputs/apk/debug/target-test-app-debug.apk`

## Development Notes

* **Dependency Management**: Plugins reference compiled classes from TestBed Core via local file paths. Ensure the core app has been compiled at least once.
* **JAR Configuration**: The `jar` task uses `DuplicatesStrategy.EXCLUDE` to safely bundle dependencies into the plugin JAR.