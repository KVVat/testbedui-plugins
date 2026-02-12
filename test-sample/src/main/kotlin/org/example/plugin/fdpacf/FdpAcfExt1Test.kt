package org.example.plugin.fdpacf

import org.example.project.adb.rules.AdbDeviceRule
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringStartsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.File
import java.nio.file.Paths
import com.malinskiy.adam.AndroidDebugBridgeClient

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@SFR("FDP_ACF_EXT.1", """
  FDP_ACF_EXT.1 App Update

  FDP_ACF.1 Security attribute based access control allows the TSF to enforce access based upon 
  security attributes and named groups of attributes. Furthermore, the TSF may have the ability to
  explicitly authorize or deny access to an object based upon security attributes.
  """)
class FdpAcfExt1Test {

  @get:Rule
  val adb = AdbDeviceRule()
  private val client: AndroidDebugBridgeClient by lazy { adb.adb }

  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val name: TestName = TestName()

  //Asset Log
  private val a: TestAssertLogger by lazy { TestAssertLogger(name) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()

  @Before
  fun setup() {
    runBlocking {
      client.execute(UninstallRemotePackageRequest("com.example.appupdate"), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/appupdate-v1-debug.apk"),
                     adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/appupdate-v2-debug.apk"),
                     adb.deviceSerial)
    }
   }

  @After
  fun teardown() {
    runBlocking {
      client.execute(UninstallRemotePackageRequest("com.example.appupdate"), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/appupdate-v1-debug.apk"),
                     adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/appupdate-v2-debug.apk"),
                     adb.deviceSerial)
    }
  }

  //@TestInformation(SFR="FDP_ACF_EXT.1/AppUpadate")
  @Test
  fun testNormalUpdate() {
    //A test for FDP_ACF_EXT.1/AppUpdate
    //UserDataProtectionTest.accessControlExt1_appUpdate_TestNormal
    println("> The test verifies apk upgrade operation works correctly.")
    runBlocking {
      //
      val file_apk_v1_debug: File =
        File(Paths.get("src", "test", "resources", "appupdate-v1-debug.apk").toUri())
      val file_apk_v2_debug: File =
        File(Paths.get("src", "test", "resources", "appupdate-v2-debug.apk").toUri())

      var ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk_v1_debug)
      assertThat(a.Msg("Verify Install apk v1 (expect=Success)"),
                 ret,StringStartsWith("Success"))

      ret =  AdamUtils.installApk(client, adb.deviceSerial, file_apk_v2_debug)
      assertThat(a.Msg("Verify Install upgraded apk v2 (expect=Success)"),
                 ret,StringStartsWith("Success"))

      //degrade
      ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk_v1_debug)
      assertThat(a.Msg("Verify Install degraded apk v1 (expect=Failure)"),
                 ret,StringStartsWith("Failure"))

      //unistall the test file before next test

      client.execute(UninstallRemotePackageRequest("com.example.appupdate"), adb.deviceSerial)
    }
  }

  //@TestInformation(SFR="FDP_ACF_EXT.1/AppUpadate")
  @Test
  fun testAbnormalUpdate() {
    println("> The test verifies apk upgrade fails if the signing keys are not-identical.")

    runBlocking {
      //
      val file_apk_v1_debug: File =
        File(Paths.get("src", "test", "resources", "appupdate-v1-debug.apk").toUri())
      val file_apk_v2_signed: File =
        File(Paths.get("src", "test", "resources", "appupdate-v2-signed.apk").toUri())

      println("Verify Install apk v1 (expect=Success)")
      var ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk_v1_debug)
      assertThat(a.Msg("Verify Install apk v1 (expect=Success)"),
                 ret,StringStartsWith("Success"))

      //Signature mismatch case
      println("Verify Install apk v2 with different signing key (expect=Failure)a")
      ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk_v2_signed)
      assertThat(a.Msg("Verify Install apk v2 with different signing key (expect=Failure)"),
                 ret,StringStartsWith("Failure"))
      //unistall the test file before next test
      client.execute(UninstallRemotePackageRequest("com.example.appupdate"), adb.deviceSerial)
    }
  }



}