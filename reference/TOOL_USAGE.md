# Tool Usage Guide

This guide explains how to build, load, execute, and monitor tests in this environment.

## 1. Building and Deploying Target Apps

APKs used in tests must be built within their respective modules in the `apps/` directory and placed in the resource directory of the core app (`testbed-core`).

### Manual Build and Copy
```bash
./gradlew :apps:openurl:assembleDebug
./gradlew :apps:openurl:copyApkToCore
```
*Note: Modules with a `copyApkToCore` task will automatically copy the built APK to `testbed-core/composeApp/resources/` upon assembly.*

## 2. Building and Loading Test Plugins

After modifying test code, rebuild the plugin JAR and reload it into the running environment.

### Build Plugin
```bash
./gradlew :test-sample:jar
```

### Reload (Refresh Test List)
Call the `mcp_my-local-server_junit_test_reload` tool. This scans the built JAR for test classes and updates the list of executable tests.

## 3. Executing Tests

### Check Available Tests
Call the `mcp_my-local-server_junit_test_list` tool to retrieve the available class and method names.

### Run a Test
Call the `mcp_my-local-server_junit_test_execute` tool with the required parameters.
```json
{
  "class_name": "org.example.plugin.ftpitc.FtpItcExt1HttpTest",
  "method_name": "testCleartextBlocked"
}
```

## 4. Monitoring Progress and Results

### Receive Real-time Logs
Periodically call the `mcp_my-local-server_junit_test_receive` tool to monitor progress.

- **Running Status**: Logs are appended to the `logs` array as they are generated.
- **Finished Status**: The `results` array contains the final Pass/Fail status and detailed failure information (e.g., stack traces).

## 5. Verified Best Practices
- **Asserting Error Logs**: Since OS error messages can vary between versions, use `contains()` for flexible matching.
- **App Instrumentation**: Add `Log.e` calls in target apps to simplify root cause analysis in automated tests.
- **Automation**: Use the Gradle `Copy` task to automate APK management and minimize deployment errors.
