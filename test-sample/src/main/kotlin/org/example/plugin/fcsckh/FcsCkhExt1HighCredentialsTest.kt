package org.example.plugin.fcsckh

import org.example.project.adb.rules.AdbDeviceRule
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.UIAutomatorSession
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandResult
import kotlinx.coroutines.delay
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

@SFR("FCS_CKH_EXT.1/High-Credentials", """
  FCS_CKH_EXT.1/Low

  FCS_CKH.1.1/Low The TSF shall support a key hierarchy for the data encryption key(s) 
  for Low user data assets.
  
  FCS_CKH.1.2/Low The TSF shall ensure that all keys in the key hierarchy are derived and/or 
  generated according to [assignment: description of how each key in the hierarchy is derived and/or
  generated, with which key lengths and according to which standards] ensuring that the key hierarchy
  uses the DUK directly or indirectly in the derivation of the data encryption key(s) for Low user 
  data assets. 
  """)
class FcsCkhExt1HighCredentialsTest {

  private val TEST_PACKAGE = "com.example.encryption"
  private val TEST_MODULE = "encryption-debug.apk"

  @get:Rule
  val adb = AdbDeviceRule()
  private val client: AndroidDebugBridgeClient by lazy { adb.adb }

  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()
  @get:Rule
  val name: TestName = TestName()
  //Asset Log
  private val a: TestAssertLogger by lazy { TestAssertLogger(name) }


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
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"),
                     adb.deviceSerial)
    }
  }


  suspend fun shellexec(cmd:String): ShellCommandResult
  {
    return client.execute(ShellCommandRequest(cmd), adb.deviceSerial)
  }


  private val PASSWORD="1234"


  @Test
  fun testSetPassCode() {
    runBlocking {

      UIAutomatorSession(adb,"com.android.Settings").run {
        turnon()
        showSettings("security")
        delay(1000)
        update()
        copyxml(Paths.get("../results/out3.xml"))
        println(activePackage)
        //freezeRotation()


        //listAllClickables()
        //unfreezeRotation()
      }
    }
  }

  @Test
  fun testBackgroundCredential() {
    runBlocking {
      //This type of test should enroll passcode each time when running the test case
      //
      val file_apk =
        File(Paths.get("src", "test", "resources", TEST_MODULE).toUri())
      val ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk)
      assertTrue(ret.startsWith("Success"))

      UIAutomatorSession(adb,TEST_PACKAGE).run {

        //turnoff()
        //turnon()

        copyxml(Paths.get("../results/out.xml"))

        val r = shellexec("am start -n ${TEST_PACKAGE}/${TEST_PACKAGE}.MainActivity"+
                " -e authRequired true"+
                " -e unlockDeviceRequired true"+
                " -e useBiometricAuth true"+
                " -e tryBackgroundKeyChainAccess false")
        println(r)
        delay(1500)

        if(update()){
          copyxml(Paths.get("../results/out2.xml"))
        } else {
          println("update failure")
        }
      }
    }

  }

  @Test
  fun testDeviceCredential() {
    runBlocking {
      //install file
      val file_apk =
        File(Paths.get("src", "test", "resources", TEST_MODULE).toUri())

      val ret = AdamUtils.installApk(client, adb.deviceSerial, file_apk)
      assertTrue(ret.startsWith("Success"))
      MatcherAssert.assertThat(
        a.Msg("Verify Install apk(expect=Success)"),
        ret, StringStartsWith("Success")
      )

      //launch application to write a file into the storage
      //am start -a com.example.ACTION_NAME -n com.package.name/com.package.name.ActivityName
      shellexec("am start -n ${TEST_PACKAGE}/${TEST_PACKAGE}.MainActivity"+
              " -e authRequired true"+
              " -e unlockDeviceRequired true"+
              " -e useBiometricAuth false"+
              " -e tryBackgroundKeyChainAccess false")

      delay(1000)

      UIAutomatorSession(adb,TEST_PACKAGE).run {
        copyxml(Paths.get("../results/out.xml"))
        tap("com.example.encryption:id/auth_test_button")
        delay(1000)
        shellexec("input text $PASSWORD")
        shellexec("input keyevent ENTER")
      }
    }
  }
}
