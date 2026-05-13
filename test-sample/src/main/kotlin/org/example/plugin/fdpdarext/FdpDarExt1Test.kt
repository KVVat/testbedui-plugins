package org.example.plugin.fdpdarext

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.misc.RebootRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.FileResource
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.logi
import org.example.project.adb.rules.AdbDeviceRule
import org.hamcrest.MatcherAssert
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.core.StringStartsWith
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.File
import java.nio.file.Paths


@SFR("FDP_DAR_EXT.1/Protected Data Encryption", """
  FDP_DAR_EXT.1.1: Encryption shall cover all protected data.
    
    [Evaluation Activity / Test]
    The evaluator shall reboot the device and verify that the application data 
    (Credential Encrypted Storage) cannot be read before first unlock (BFU state), 
    while verifying that Device Encrypted Storage (DES) remains accessible for 
    Direct Boot aware applications.
    
    (FCS_CKH_EXT.1/Low in MDFPP 3.2)
  """,category="encryption")
class FdpDarExt1Test {

  private val TEST_PACKAGE = "com.example.directboot"
  private val TEST_MODULE = "directboot.apk"

  @get:Rule
  val adb = AdbDeviceRule()
    private val client: AndroidDebugBridgeClient by lazy { adb.adb }
     private val serial get() = adb.deviceSerial
  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()
  @get:Rule
  val testName: TestName = TestName()
  private val assert: TestAssertLogger by lazy { TestAssertLogger(testName) }

    private val TEST_APK by lazy { FileResource(TEST_MODULE) }

  @Before
  fun setup() {
      runBlocking {
          logi("Setting up ${testName.methodName}...")
          try { client.execute(ShellCommandRequest("input keyevent KEYCODE_WAKEUP"), adb.deviceSerial) } catch (e: Exception) {}
          try { client.execute(ShellCommandRequest("svc power stayon true"), adb.deviceSerial) } catch (e: Exception) {}
          try { client.execute(ShellCommandRequest("am force-stop $TEST_PACKAGE"), adb.deviceSerial) } catch (e: Exception) {}
      }
  }

  @After
  fun teardown() {
      runBlocking {
          logi("Tearing down ${testName.methodName}...")
          // try { AdamUtils.uninstallApk(client, serial, TEST_PACKAGE) } catch (e: Exception) {}
      }
  }

  @Test
  fun testDeviceEncryptedStorage() {
      runBlocking {
          //install file

          val ret = AdamUtils.installApk(client, adb.deviceSerial, TEST_APK, reinstall = true)
          Assert.assertTrue(ret.startsWith("Success"))
          MatcherAssert.assertThat(
              assert.msg("Verify Install apk v1 (expect=Success)"),
              ret, StringStartsWith("Success")
          )

          //launch application to write a file into the storage
          //am start -a com.example.ACTION_NAME -n com.package.name/com.package.name.ActivityName
          AdamUtils.clearLogcat(adb)
          async {
              client.execute(
                  ShellCommandRequest("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $TEST_PACKAGE"),
                  adb.deviceSerial
              )
          }
          var result: AdamUtils.LogcatResult? =
              AdamUtils.waitLogcatLine(200, "FCS_CKH_EXT_TEST", adb, clear = false, contentFilter = "Booted")
          //assertThat { result }.isNotNull()
          errs.checkThat(
              assert.msg("Check The application booted.(It prepares directboot.)"),
              result!!.text,
              containsString("Booted")
          )
          Thread.sleep(1000 * 5)
          // NOTE: On Android 17, ADB connection in BFU (Before First Unlock) state
          // may be restricted. On devices with this restriction, manual unlock after reboot
          // is required to let ADB connect and read logs.
          // 2. Users need to unlock the device quickly
          AdamUtils.clearLogcat(adb)

          //(Require)Reboot Device
          //1. We expect the bootloader of the device is unlocked.
          //2. Users need to relaunch the device quickly
          AdamUtils.clearLogcat(adb)
          try {
              client.execute(ShellCommandRequest("svc power reboot"), adb.deviceSerial)
          } catch (e: Exception) {
              logi("Reboot signal sent. (Expected ADB disconnection: ${e.message})")
          }
          Thread.sleep(1000*5)
          adb.waitBoot(180_000L)
          //Thread.sleep(1000 * 10)
          logi("** Reconnected **")
          result = AdamUtils.waitLogcatLine(500, "FCS_CKH_EXT_TEST", adb, clear = false, contentFilter = "des=Success,ces=Failed")
          if (result == null) {
              result = AdamUtils.LogcatResult("", "<null>")
          }
          logi("Matched line text: ${result.text}")

          // Evaluates below behaviours. Application will be triggered by LOCKED_BOOT_COMPLETED action.
          // 1. Check if we can access to the DES(Device Encrypted Storage)
          // 2. Check we can not access to the CES
          errs.checkThat(
              assert.msg("Check if we can access to the DES/We can not accees to CES."),
              result.text,
              containsString("des=Success,ces=Failed")
          )
      }
  }
}