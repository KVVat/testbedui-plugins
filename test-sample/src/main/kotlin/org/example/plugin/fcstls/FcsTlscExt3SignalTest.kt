package org.example.plugin.fcstls

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.example.plugin.fcstls.mock.Tls13DowngradeSignalMockServer
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.TlsTestUtils
import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.project.adb.rules.AdbDeviceRule
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher

/**
 * FCS_TLSC_EXT.3.1 — TLS 1.3 → 1.2 downgrade signal detection (RFC 8446 §4.1.3).
 *
 * Verifies the TOE aborts the handshake when a server negotiates TLS 1.2 while
 * signalling "I am TLS 1.3 capable" by setting the last 8 bytes of
 * ServerHello.random to 44 4F 57 4E 47 52 44 01.
 *
 * This test is independent from [FcsTlscExt3DowngradeTest] (which uses the
 * BouncyCastle-based mock and a two-connection scenario). Here the scenario is
 * a single connection — which is what RFC 8446 §4.1.3 actually describes.
 */
@SFR("FCS_TLSC_EXT.3.1/Signal", "Detect TLS 1.3→1.2 downgrade signal (RFC 8446 §4.1.3)", "network")
class FcsTlscExt3SignalTest {
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

    private var server: Tls13DowngradeSignalMockServer? = null
    private val serverPort = 8446

    @Before
    fun setup() {
        val serial = adb.deviceSerial

        runBlocking {
            client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), serial)
        }

        server = Tls13DowngradeSignalMockServer(serverPort).also { it.start() }

        try {
            ProcessBuilder("adb", "-s", serial, "reverse", "tcp:$serverPort", "tcp:$serverPort")
                .start().waitFor()
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
            ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:$serverPort")
                .start().waitFor()
            logi("[JUnit] adb reverse removed for port $serverPort")
        } catch (e: Exception) {
            loge("[JUnit] adb reverse remove failed: ${e.message}")
        }
    }

    @Test
    fun testDowngradeSignalRejected() {
        val hostName = "https://localhost:$serverPort/"

        logi("[JUnit] Single connection — expecting TOE to reject downgrade signal")
        val resp = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "downgrade_signal", hostName)
        val httpRet = resp.httpResponse
        logi("[JUnit] HTTP response: $httpRet")

        // Give the mock a moment to finalise its observation of the client's reaction.
        Thread.sleep(500)
        val reaction = server?.lastClientReaction ?: Tls13DowngradeSignalMockServer.ClientReaction.NONE
        logi("[JUnit] Mock-observed client reaction: $reaction")

        // --- HTTP-layer assertion (auxiliary) --------------------------------
        errs.checkThat(
            a.msg("Connection must NOT succeed (HTTP 200 would indicate downgrade accepted)"),
            httpRet == "200", IsEqual(false)
        )
        errs.checkThat(
            a.msg("HTTP response should not be a test-harness error"),
            httpRet.contains("marker not found") ||
                httpRet.contains("Url is not specified"),
            IsEqual(false)
        )

        // --- TLS-layer assertion (primary) -----------------------------------
        // Any of the following count as "TOE detected and aborted":
        //   - illegal_parameter fatal alert (RFC-mandated response)
        //   - any other fatal alert (still a rejection)
        //   - FIN without alert (some stacks just drop the connection)
        val aborted = reaction in setOf(
            Tls13DowngradeSignalMockServer.ClientReaction.ILLEGAL_PARAMETER_ALERT,
            Tls13DowngradeSignalMockServer.ClientReaction.FATAL_ALERT,
            Tls13DowngradeSignalMockServer.ClientReaction.CONNECTION_CLOSED
        )
        errs.checkThat(
            a.msg("Mock must observe TOE aborting the handshake (alert / FIN), saw=$reaction"),
            aborted, IsEqual(true)
        )

        // The strongest guarantee: client sent illegal_parameter (47) specifically.
        errs.checkThat(
            a.msg("Preferred: client emits illegal_parameter(47) fatal alert — saw=$reaction"),
            reaction == Tls13DowngradeSignalMockServer.ClientReaction.ILLEGAL_PARAMETER_ALERT,
            IsEqual(true)
        )

        // Anti-regression: TOE must not silently continue the handshake.
        errs.checkThat(
            a.msg("TOE must NOT continue handshake after downgrade signal"),
            reaction == Tls13DowngradeSignalMockServer.ClientReaction.HANDSHAKE_CONTINUED,
            IsEqual(false)
        )
    }
}
