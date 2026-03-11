# Test Creation Conventions

This document outlines the conventions and tools for creating tests in this project.

## 1. Logging

Use the extension functions defined in the `org.example.plugin.utils` package for logging during tests.

- `logd(message)`: Debug information (DEBUG level)
- `logi(message)`: General information (INFO level)
- `logp(message)`: Test passed information (PASS level)
- `logw(message)`: Warning (WARN level)
- `loge(message)`: Error information (ERROR level)

### Target App Logging Note
When an exception occurs in a target app (`apps/` directory), ensure you explicitly call `Log.e(TAG, ...)` in addition to returning `Result.failure`. This allows the test plugin to identify the root cause via Logcat.

## 2. @SFR Annotation (Required)

To relay test purposes and requirements to `testbed-core`, every test class or method must be annotated with **`@SFR(title = "...", description = "...")`**.

```kotlin
import org.example.plugin.utils.SFR

/**
 * Verification of trusted communication channels.
 */
@SFR(
    title = "FTP_ITC_EXT.1",
    description = "Verify that cleartext HTTP communication is correctly blocked by the OS Network Security Policy."
)
class FtpItcExt1HttpTest { ... }
```

- **title**: Requirement ID or a short title for the test case.
- **description**: A detailed explanation of what is being verified.

## 3. Handling OS Version Differences

Newer OS versions (e.g., Android 15/16) may have slightly different system error messages. When asserting log outputs, prefer using `contains()` with multiple keywords (e.g., `CLEARTEXT` and `not permitted`) rather than exact string matching to ensure robustness across versions.

## 4. Basic Test Structure

- **JUnit 4**: Use `org.junit.Test` annotation.
- **AdbDeviceRule**: Define as `@get:Rule` if device interaction is required.
- **setUp/tearDown**: If using `runBlocking`, ensure the method returns `Unit` by wrapping the logic inside the block.

```kotlin
@Before
fun setUp() {
    runBlocking {
        // Async initialization logic
    }
}
```
