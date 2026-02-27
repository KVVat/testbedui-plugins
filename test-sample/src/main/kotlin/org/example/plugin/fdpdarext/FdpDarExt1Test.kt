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
  """)
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
          try { AdamUtils.uninstallApk(client, serial, TEST_PACKAGE) } catch (e: Exception) {}
      }
  }

  @After
  fun teardown() {
      runBlocking {
          logi("Setting up ${testName.methodName}...")
          try { AdamUtils.uninstallApk(client, serial, TEST_PACKAGE) } catch (e: Exception) {}
      }
  }

  @Test
  fun testDeviceEncryptedStorage() {
      runBlocking {
          //install file

          val ret = AdamUtils.installApk(client, adb.deviceSerial, TEST_APK)
          Assert.assertTrue(ret.startsWith("Success"))
          MatcherAssert.assertThat(
              assert.msg("Verify Install apk v1 (expect=Success)"),
              ret, StringStartsWith("Success")
          )

          //launch application to write a file into the storage
          //am start -a com.example.ACTION_NAME -n com.package.name/com.package.name.ActivityName
          async {
              client.execute(
                  ShellCommandRequest("am start ${TEST_PACKAGE}/${TEST_PACKAGE}.MainActivity"),
                  adb.deviceSerial
              )
          }
          var result: AdamUtils.LogcatResult? =
              AdamUtils.waitLogcatLine(50, "Info/FCS_CKH_EXT_TEST", adb)
          //assertThat { result }.isNotNull()
          errs.checkThat(
              assert.msg("Check The application booted.(It prepares directboot.)"),
              result!!.text,
              StringStartsWith("Booted")
          )
          Thread.sleep(1000 * 5)
          //(Require)Reboot Device
          //1. We expect the bootloader of the device is unlocked.
          //2. Users need to relaunch the device quickly
          try {
              client.execute(ShellCommandRequest("svc power reboot"), adb.deviceSerial)
          } catch (e: Exception) {
              logi("Reboot signal sent. (Expected ADB disconnection: ${e.message})")
          }
          Thread.sleep(1000*5)
          adb.waitBoot()
          //Thread.sleep(1000 * 10)
          logi("** Reconnected **")
          result = AdamUtils.waitLogcatLine(500, "FCS_CKH_EXT_TEST", adb)
          if (result == null) {
              result = AdamUtils.LogcatResult("", "<null>")
          }

          // Evaluates below behaviours. Application will be triggered by LOCKED_BOOT_COMPLETED action.
          // 1. Check if we can access to the DES(Device Encrypted Storage)
          // 2. Check we can not access to the CES
          errs.checkThat(
              assert.msg("Check if we can access to the DES/We can not accees to CES."),
              result.text,
              StringStartsWith("des=Success,ces=Failed")
          )
      }
  }
}