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
import org.example.plugin.fcstls.mock.Tls12InsecureMockServer
import org.example.plugin.utils.TlsTestUtils
import org.example.plugin.utils.logi
import org.example.plugin.utils.loge

/**
 * FCS_TLSC_EXT.4: Secure Renegotiation
 * NOTE: In the current implementation, the test passes because the client aborts the
 * handshake with Alert 47 (illegal_parameter). However, the underlying reason logged
 * by the client (BoringSSL) was 'WRONG_CERTIFICATE_TYPE' (even after providing a server
 * certificate from badssl.com). This effectively fails the connection (which is the goal),
 * but does not strictly isolate the lack of 'renegotiation_info' extension as the sole
 * cause of rejection.
 *
 * For strict testing in the future, we need to prepare a test server that has a certificate
 * within the valid certificate chain recognized by BoringSSL and performs invalid renegotiation.
 */
@SFR("FCS_TLSC_EXT.4.1", "Verify secure renegotiation rejection (rejection of insecure ServerHello)", "network")
class FcsTlscExt4RenegotiationTest {

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

    private var server: Tls12InsecureMockServer? = null
    private val serverPort = 8448

    @Before
    fun setup() {
        val serial = adb.deviceSerial
        
        runBlocking {
            client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), serial)
        }
        
        server = Tls12InsecureMockServer(serverPort).also { it.start() }
        
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
    fun testInsecureConnectionRejected() {
        val hostName = "https://localhost:$serverPort/"
        
        logi("[JUnit] Expecting TOE to reject server without secure renegotiation support")
        val resp = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "insecure_reneg", hostName)
        val httpRet = resp.httpResponse
        logi("[JUnit] HTTP response: $httpRet")
        
        Thread.sleep(500)
        val reaction = server?.lastClientReaction ?: Tls12InsecureMockServer.ClientReaction.NONE
        logi("[JUnit] Mock-observed client reaction: $reaction")
        
        // Assertions
        errs.checkThat(
            a.msg("Connection must NOT succeed"),
            httpRet == "200", IsEqual(false)
        )
        
        // Check if mock observed an abort (Fatal alert or close)
        val aborted = reaction in setOf(
            Tls12InsecureMockServer.ClientReaction.FATAL_ALERT,
            Tls12InsecureMockServer.ClientReaction.CONNECTION_CLOSED
        )
        errs.checkThat(
            a.msg("Mock must observe TOE aborting handshake, saw=$reaction"),
            aborted, IsEqual(true)
        )
        
        errs.checkThat(
            a.msg("TOE must NOT continue handshake"),
            reaction == Tls12InsecureMockServer.ClientReaction.HANDSHAKE_CONTINUED,
            IsEqual(false)
        )
    }
}
