package com.example.test_suites



import com.example.test_suites.rule.AdbDeviceRule
import com.example.test_suites.utils.ADSRPTestWatcher
import com.example.test_suites.utils.AdamUtils
import com.example.test_suites.utils.LogcatResult
import com.example.test_suites.utils.SFR
import com.example.test_suites.utils.TestAssertLogger
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.misc.RebootRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringStartsWith
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.File
import java.nio.file.Paths

@SFR("FCS_CKH_EXT.1/Low", """
  FCS_CKH_EXT.1/Low

  FCS_CKH.1.1/Low The TSF shall support a key hierarchy for the data encryption key(s) 
  for Low user data assets.
  
  FCS_CKH.1.2/Low The TSF shall ensure that all keys in the key hierarchy are derived and/or 
  generated according to [assignment: description of how each key in the hierarchy is derived and/or
  generated, with which key lengths and according to which standards] ensuring that the key hierarchy
  uses the DUK directly or indirectly in the derivation of the data encryption key(s) for Low user 
  data assets. 
  """)
class FCS_CKH_EXT1 {

  private val TEST_PACKAGE = "com.example.directboot"
  private val TEST_MODULE = "directboot-debug.apk"

  @Rule
  @JvmField
  val adb = AdbDeviceRule()
  private val client:AndroidDebugBridgeClient = adb.adb

  @Rule @JvmField
  var watcher: TestWatcher = ADSRPTestWatcher(adb)
  @Rule @JvmField
  var errs: ErrorCollector = ErrorCollector()
  @Rule @JvmField
  var name: TestName = TestName()
  //Asset Log
  var a: TestAssertLogger = TestAssertLogger(name)


  @Before
  fun setup() {
    runBlocking {
      client.execute(UninstallRemotePackageRequest(TEST_PACKAGE), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"),
                     adb.deviceSerial)



    }

  }
  @After
  fun teardown() {
    runBlocking {
      //client.execute(UninstallRemotePackageRequest(TEST_PACKAGE), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"),
                     adb.deviceSerial)
    }
  }



  @Test
  fun testDeviceEncryptedStorage() {
    runBlocking {
      //install file
      val file_apk =
        File(Paths.get("src", "test", "resources", TEST_MODULE).toUri())

      val ret = AdamUtils.InstallApk(file_apk, false,adb)
      assertTrue(ret.startsWith("Success"))
      MatcherAssert.assertThat(
        a.Msg("Verify Install apk v1 (expect=Success)"),
        ret, StringStartsWith("Success")
      )

      //launch application to write a file into the storage
      //am start -a com.example.ACTION_NAME -n com.package.name/com.package.name.ActivityName
      async {
        client.execute(ShellCommandRequest("am start ${TEST_PACKAGE}/${TEST_PACKAGE}.MainActivity"),
                       adb.deviceSerial)
      }
      var result:LogcatResult?
        = AdamUtils.waitLogcatLine(50,"FCS_CKH_EXT_TEST",adb)
      //assertThat { result }.isNotNull()
      errs.checkThat(a.Msg("Check The application booted.(It prepares directboot.)"),
                     result!!.text,
                     StringStartsWith("Booted") )


      Thread.sleep(1000*5)

      //(Require)Reboot Device
      //1. We expect the bootloader of the device is unlocked.
      //2. Users need to relaunch the device quickly
      client.execute(request = RebootRequest(), serial = adb.deviceSerial)
      println("> ** Rebooting : Please Reboot Device **")
      Thread.sleep(1000*10)
      //Note:  the connection to the adb server will be dismissed during the rebooting
      println("> ** Maybe it requires manual operation : Please Reboot the target device as fast as possible **")
      adb.waitBoot()
      Thread.sleep(1000*5)
      println("> ** Reconnected")
      result = AdamUtils.waitLogcatLine(200,"FCS_CKH_EXT_TEST",adb)
      if(result == null){
        result = LogcatResult("","<null>")
      }

      // Evaluates below behaviours. Application will be triggered by LOCKED_BOOT_COMPLETED action.
      // 1. Check if we can access to the DES(Device Encrypted Storage)
      // 2. Check we can not access to the CES
      errs.checkThat(a.Msg("Check if we can access to the DES/We can not accees to CES."),
                     result.text,
                     StringStartsWith("des=Success,ces=Failed") )


    }
  }
}
