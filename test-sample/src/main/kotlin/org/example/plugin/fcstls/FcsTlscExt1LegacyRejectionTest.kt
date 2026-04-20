package org.example.plugin.fcstls

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.hamcrest.core.IsEqual
import org.junit.After
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
import org.example.plugin.fcstls.mock.TlsLegacyRejectMockServer
import org.example.plugin.utils.TlsTestUtils
import org.example.plugin.utils.logi
import org.example.plugin.utils.loge

/**
 * FCS_TLSC_EXT.1.1: Active Rejection of Legacy Protocols
 * Verify that the TOE actively rejects connections to servers that propose legacy protocols (SSLv3).
 */
@SFR("FCS_TLSC_EXT.1.1", "Verify active rejection of legacy protocols (SSLv3)", "network")
class FcsTlscExt1LegacyRejectionTest {

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

    private var server: TlsLegacyRejectMockServer? = null
    private val serverPort = 8449

    @Before
    fun setup() {
        val serial = adb.deviceSerial
        
        runBlocking {
            client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), serial)
        }
        
        server = TlsLegacyRejectMockServer(serverPort).also { it.start() }
        
        // Setup adb reverse
        try {
            val process = ProcessBuilder("adb", "-s", serial, "reverse", "tcp:$serverPort", "tcp:$serverPort").start()
            process.waitFor()
            logi("[JUnit] adb reverse set for port $serverPort")
        } catch (e: Exception) {
            loge("[JUnit] adb reverse failed: ${e.message}")
        }
    }

    @After
    fun teardown() {
        val serial = adb.deviceSerial
        server?.stop()
        try {
            val process = ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:$serverPort").start()
            process.waitFor()
            logi("[JUnit] adb reverse removed for port $serverPort")
        } catch (e: Exception) {
            loge("[JUnit] adb reverse remove failed: ${e.message}")
        }
    }

    @Test
    fun testLegacyProtocolRejected() {
        val hostName = "https://localhost:$serverPort/"
        
        logi("[JUnit] Expecting TOE to actively reject SSLv3 ServerHello")
        val resp = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "legacy_reject", hostName)
        val httpRet = resp.first
        logi("[JUnit] HTTP response: $httpRet")
        
        Thread.sleep(500)
        val reaction = server?.lastClientReaction ?: TlsLegacyRejectMockServer.ClientReaction.NONE
        logi("[JUnit] Mock-observed client reaction: $reaction")
        
        // Assertions
        errs.checkThat(
            a.msg("Connection must NOT succeed"),
            httpRet == "200", IsEqual(false)
        )
        
        // Check if mock observed an abort
        val aborted = reaction in setOf(
            TlsLegacyRejectMockServer.ClientReaction.FATAL_ALERT,
            TlsLegacyRejectMockServer.ClientReaction.CONNECTION_CLOSED
        )
        errs.checkThat(
            a.msg("Mock must observe TOE aborting handshake, saw=$reaction"),
            aborted, IsEqual(true)
        )
        
        errs.checkThat(
            a.msg("TOE must NOT continue handshake"),
            reaction == TlsLegacyRejectMockServer.ClientReaction.HANDSHAKE_CONTINUED,
            IsEqual(false)
        )
    }
}
