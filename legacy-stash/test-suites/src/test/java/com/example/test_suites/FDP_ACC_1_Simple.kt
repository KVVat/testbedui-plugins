package com.example.test_suites

import com.example.test_suites.rule.AdbDeviceRule
import com.example.test_suites.utils.ADSRPTestWatcher
import com.example.test_suites.utils.AdamUtils
import com.example.test_suites.utils.LogcatResult
import com.example.test_suites.utils.SFR
import com.example.test_suites.utils.TestAssertLogger
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
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

//FPR_PSE.1

@SFR("FDP_ACC_1", """
  FDP_ACC_1. Subset access control

  The terms object and subject refer to generic elements in the TOE. For a policy to be 
  implementable, the entities must be clearly identified. For a PP, the objects and operations might
  be expressed as types such as: named objects, data repositories, observe accesses, etc. 
  For a specific system these generic terms (subject, object) must be refined, e.g. files, registers,
  ports, daemons, open calls, etc.
  This component specifies that the policy cover some well-defined set of operations on some subset 
  of the objects. It places no constraints on any operations outside the set - 
  including operations on objects for which other operations are controlled.
  """)
class FDP_ACC_1_Simple {

  private val TEST_PACKAGE = "com.example.assets"
  private val TEST_MODULE = "assets-debug.apk"
  private val LONG_TIMEOUT = 5000L
  private val SHORT_TIMEOUT = 1000L

  @Rule
  @JvmField
  val adb = AdbDeviceRule()
  val client = adb.adb

  @Rule @JvmField
  var watcher: TestWatcher = ADSRPTestWatcher(adb)
  @Rule @JvmField
  var name: TestName = TestName()

  //Asset Log
  var a: TestAssertLogger = TestAssertLogger(name)
  @Rule @JvmField
  var errs: ErrorCollector = ErrorCollector()
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


  // inline fun <T> checkThatNow(reason:String, value:T, matcher:Matcher<T>){
  //   errs.checkThat(reason, value, matcher).
  //
  // }
  @Test
  fun testUserAssets()
  {
    runBlocking {
      val file_apk =
        File(Paths.get("src", "test", "resources", TEST_MODULE).toUri())
      println("Found file to install:"+file_apk.exists())
      var response: ShellCommandResult
      var result: LogcatResult?
      var ret = AdamUtils.InstallApk(file_apk,false,adb)
      assertThat(a.Msg("Install Package"),
                 ret,StringStartsWith("Success"))

      println("Install done")

      Thread.sleep(SHORT_TIMEOUT*2)

      //launch application and prepare
      response = client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.PrepareActivity"), adb.deviceSerial)
      assertThat(a.Msg("Preparing Test Files with opening PrepareActivity"),
                 response.output,StringStartsWith("Starting"))

      Thread.sleep(LONG_TIMEOUT)
      response = client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"), adb.deviceSerial)
      assertThat(a.Msg("Check file acccess via MainActivity"),
                 response.output,StringStartsWith("Starting"))

      println("> Test result shows the file acccess status below. 0=Preference/1=Private File/2=Media Storage/3=Database")
      result = AdamUtils.waitLogcatLine(100,"FDP_ACC_1_TEST",adb)
      assertThat(a.Msg("Check Output of the Test Package"),
                 result?.text,IsEqual("Test Result:true/true/true/true"))
      println(result?.text!!.trim())
      //uninstall application =>
      response = client.execute(UninstallRemotePackageRequest(TEST_PACKAGE), adb.deviceSerial)
      assertThat(a.Msg("Uninstall Test Package"),
                 response.output,StringStartsWith("Success"))
      //install application => files execpt media storage will be removed,
      //The app will lost the access permission to the owner file once uninstall it.
      //so we should reinstall it with -g option to enable read_media_storage permission
      ret = AdamUtils.InstallApk(file_apk,false,adb)
      assertThat(a.Msg("Install Package"),
                 ret,StringStartsWith("Success"))
      Thread.sleep(SHORT_TIMEOUT*2)

      response = client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"), adb.deviceSerial)
      assertThat(a.Msg("Check file access via MainActivity"),
                 response.output,StringStartsWith("Starting"))

      result = AdamUtils.waitLogcatLine(100,"FDP_ACC_1_TEST",adb)
      println(result?.text!!.trim())
      assertThat(a.Msg("Check Output of the Test Package"),
                 result?.text,IsEqual("Test Result:false/false/true/false"))
    }
  }

}