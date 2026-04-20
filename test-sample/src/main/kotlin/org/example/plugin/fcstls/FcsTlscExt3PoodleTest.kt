package org.example.plugin.fcstls

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.example.plugin.fcstls.mock.PoodleFallbackMockServer
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
 * FCS_TLSC_EXT.3.1 — POODLE-style fallback verification (RFC 7507 / CVE-2014-3566).
 *
 * The TOE is considered safe against POODLE-style downgrade if EITHER:
 *
 *   (a) it does NOT retry the handshake with a lower protocol version after the
 *       first attempt fails (the modern/recommended posture), OR
 *   (b) if it does retry with a lower version, every such retry ClientHello
 *       MUST contain `TLS_FALLBACK_SCSV` (0x5600) per RFC 7507.
 *
 * The mock server sends a fatal `handshake_failure` alert to the first
 * ClientHello and records every subsequent ClientHello for analysis.
 */
@SFR("FCS_TLSC_EXT.3.1/Poodle", "POODLE-style fallback protection (TLS_FALLBACK_SCSV)", "network")
class FcsTlscExt3PoodleTest {

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

    private var server: PoodleFallbackMockServer? = null
    private val serverPort = 8447

    @Before
    fun setup() {
        val serial = adb.deviceSerial

        runBlocking {
            client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), serial)
        }

        server = PoodleFallbackMockServer(serverPort).also { it.start() }

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
    fun testNoUnsafeFallback() {
        val hostName = "https://localhost:$serverPort/"

        logi("[JUnit] Triggering handshake; mock will reject with handshake_failure")
        val resp = TlsTestUtils.tlsCapturePacket(client, adb.deviceSerial, "poodle_fallback", hostName)
        val httpRet = resp.httpResponse
        logi("[JUnit] HTTP response: $httpRet")

        // Give time for any fallback retries to reach the mock.
        Thread.sleep(1500)
        val hs = server?.handshakes ?: emptyList()
        logi("[JUnit] Observed ${hs.size} ClientHello(s):")
        hs.forEach { logi("[JUnit]   - $it") }

        // --- HTTP-layer assertion: connection must not succeed ---------------
        errs.checkThat(
            a.msg("HTTPS connection must not succeed (server rejects first handshake)"),
            httpRet == "200", IsEqual(false)
        )

        // --- At least one ClientHello must have reached the mock -------------
        errs.checkThat(
            a.msg("Mock must observe at least one ClientHello (otherwise nothing was tested)"),
            hs.isNotEmpty(), IsEqual(true)
        )
        if (hs.isEmpty()) return

        // --- Fallback-version analysis --------------------------------------
        val first = hs.first()
        val retries = hs.drop(1)

        if (retries.isEmpty()) {
            // Case (a): modern secure behaviour — no fallback attempt.
            logi("[JUnit] No fallback retry observed — TOE does not downgrade on handshake failure")
            errs.checkThat(
                a.msg("Secure posture: TOE did not retry on failure — pass"),
                true, IsEqual(true)
            )
        } else {
            // Case (b): retries exist. For every retry whose negotiated-max version
            // is lower than the first attempt's, SCSV must be present.
            val firstMax = negotiatedMax(first)
            for (r in retries) {
                val rMax = negotiatedMax(r)
                val isLower = rMax < firstMax
                logi(
                    "[JUnit] Retry seq=${r.sequence} " +
                        "first_max=0x%04x retry_max=0x%04x is_lower=$isLower scsv=${r.containsFallbackScsv}"
                            .format(firstMax, rMax)
                )
                if (isLower) {
                    errs.checkThat(
                        a.msg(
                            "Lower-version retry (seq=${r.sequence}) MUST include " +
                                "TLS_FALLBACK_SCSV (0x5600) — RFC 7507"
                        ),
                        r.containsFallbackScsv, IsEqual(true)
                    )
                }
            }
        }

        // --- Absolute red line: no ClientHello may advertise SSLv3 or below --
        for (r in hs) {
            val min = r.supportedVersions.minOrNull() ?: r.legacyVersion
            errs.checkThat(
                a.msg(
                    "ClientHello (seq=${r.sequence}) must not advertise SSLv3 " +
                        "(min version observed = 0x%04x)".format(min)
                ),
                min <= 0x0300, IsEqual(false)
            )
        }
    }

    /**
     * Highest protocol version the ClientHello is willing to accept.
     * Prefers supported_versions extension; falls back to legacy_version.
     */
    private fun negotiatedMax(r: PoodleFallbackMockServer.HandshakeRecord): Int =
        r.supportedVersions.maxOrNull() ?: r.legacyVersion
}
