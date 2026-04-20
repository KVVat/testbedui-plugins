package org.example.plugin.fcstls

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.example.project.JUnitBridge
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.example.project.adb.rules.AdbDeviceRule
import com.malinskiy.adam.AndroidDebugBridgeClient
import org.example.plugin.fcstls.mock.DowngradeMockServer
import org.example.plugin.utils.TlsTestUtils
import org.example.plugin.utils.logi // Import shorter logging
import org.example.plugin.utils.loge // Import shorter logging

/**
 * FCS_TLSC_EXT.3.1: Downgrade Protection
 * Verify that the TOE detects and prevents downgrade attacks during resumption.
 */
@SFR("FCS_TLSC_EXT.3.1", "Verify downgrade protection during resumption", "network")
class FcsTlscExt3DowngradeTest {

    @get:Rule
    val adb = AdbDeviceRule()
    private val client: AndroidDebugBridgeClient by lazy { adb.adb }

    @get:Rule
    val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
    @get:Rule
    val testname: TestName = TestName()

    private val a: TestAssertLogger by lazy { TestAssertLogger(testname) }
    @get:Rule
    val errs: ErrorCollector = ErrorCollector()

    private var server: DowngradeMockServer? = null
    private val serverPort = 8445

    @Before
    fun setup() {
        val serial = adb.deviceSerial
        
        runBlocking {
            client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), serial)
        }
        
        val certFile = File(JUnitBridge.resourceDir, "badssl.com-client.p12")
        server = DowngradeMockServer(serverPort, certFile.absolutePath, "badssl.com")
        server?.start()
        
        // Setup adb reverse
        try {
            val process = ProcessBuilder("adb", "-s", serial, "reverse", "tcp:$serverPort", "tcp:$serverPort").start()
            process.waitFor()
            logi("[JUnit] Executed adb reverse for port $serverPort")
        } catch (e: Exception) {
            loge("[JUnit] Failed to execute adb reverse: ${e.message}")
        }
    }

    @After
    fun teardown() {
        val serial = adb.deviceSerial
        server?.stop()
        try {
            val process = ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:$serverPort").start()
            process.waitFor()
            logi("[JUnit] Removed adb reverse for port $serverPort")
        } catch (e: Exception) {
            loge("[JUnit] Failed to remove adb reverse: ${e.message}")
        }
    }

    @Test
    fun testDowngradeRejectionOnResumption() {
        val hostName = "https://localhost:$serverPort/"
        
        // 1. First connection: Should succeed as TLS 1.3
        logi("[JUnit] Starting first connection (expecting success)...")
        val resp1 = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "downgrade_init", hostName)
        val httpret1 = resp1.first
        logi("[JUnit] First HTTP response: $httpret1")
        
        // 2. Second connection: Server will force TLS 1.2 and inject signal. Client should reject!
        logi("[JUnit] Starting second connection (expecting rejection)...")
        val resp2 = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "downgrade_attack", hostName)
        val httpret2 = resp2.first
        logi("[JUnit] Second HTTP response: $httpret2")
        
        // Assertions
        errs.checkThat(a.msg("First connection should succeed"), httpret1 == "200", IsEqual(true))
        errs.checkThat(a.msg("Second connection should be rejected"), httpret2 == "200", IsEqual(false))
        errs.checkThat(a.msg("Second connection should not be timeout"), httpret2.contains("timeout"), IsEqual(false))
        errs.checkThat(a.msg("Second connection should be specified"), httpret2.contains("Url is not specified"), IsEqual(false))
        errs.checkThat(a.msg("Second connection marker found"), httpret2.contains("marker not found"), IsEqual(false))
    }
}
