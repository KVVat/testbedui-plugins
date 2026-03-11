package org.example.plugin.fptaex

import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.SFR
import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.plugin.utils.logp
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/**
 * Domain Isolation - The TSF shall maintain a security domain for its own execution 
 * that protects it from interference and tampering by untrusted subjects.
 */
@SFR(
    title = "FPT_AEX_EXT.4.1",
    description = "Verify that system partitions (/system, /vendor) are read-only and protect the TSF from unauthorized modifications."
)
class FptAexExt4Test {

    @get:Rule
    val adbDeviceRule = AdbDeviceRule()
    val client get() = adbDeviceRule.adb
    val serial get() = adbDeviceRule.deviceSerial

    @Test
    fun testSystemPartitionIsReadOnly() = runBlocking {
        logi("Starting FptAexExt4Test (System Partition Protection)...")

        val testFile = "/system/test_tamper.txt"
        logi("Attempting to create a file in system partition: $testFile")

        // Attempt to create a file using shell
        val result = client.execute(ShellCommandRequest("touch $testFile 2>&1"), serial)
        val output = String(result.stdout).trim()

        logi("Command output: $output")

        // Expected results: "Read-only file system" or "Permission denied"
        if (output.contains("Read-only file system") || output.contains("Permission denied")) {
            logp("SUCCESS: System partition is protected. Modification denied as expected.")
        } else if (output.isEmpty()) {
            // Check if file was actually created (unlikely on modern Android)
            val checkResult = client.execute(ShellCommandRequest("ls $testFile 2>&1"), serial)
            val checkOutput = String(checkResult.stdout)
            if (checkOutput.contains("No such file or directory")) {
                logp("SUCCESS: File was not created, even though touch didn't return an error.")
            } else {
                loge("FAILURE: Successfully created a file in /system! Partition is not read-only.")
                Assert.fail("System partition is writable")
            }
        } else {
            loge("FAILURE: Unexpected output during modification attempt: $output")
            Assert.fail("Unexpected result")
        }
    }

    @Test
    fun testVendorPartitionIsReadOnly() = runBlocking {
        logi("Starting FptAexExt4Test (Vendor Partition Protection)...")

        val testFile = "/vendor/test_tamper.txt"
        logi("Attempting to create a file in vendor partition: $testFile")

        val result = client.execute(ShellCommandRequest("touch $testFile 2>&1"), serial)
        val output = String(result.stdout).trim()

        logi("Command output: $output")

        if (output.contains("Read-only file system") || output.contains("Permission denied")) {
            logp("SUCCESS: Vendor partition is protected.")
        } else {
            val checkResult = client.execute(ShellCommandRequest("ls $testFile 2>&1"), serial)
            val checkOutput = String(checkResult.stdout)
            if (checkOutput.contains("No such file or directory")) {
                logp("SUCCESS: Vendor partition protected.")
            } else {
                loge("FAILURE: Vendor partition is writable!")
                Assert.fail("Vendor partition is writable")
            }
        }
    }
}
