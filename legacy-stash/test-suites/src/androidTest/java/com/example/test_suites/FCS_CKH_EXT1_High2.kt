package com.example.test_suites

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import assertk.assertThat
import assertk.assertions.isNotEqualTo
import com.example.test_suites.utils.ADSRPTestWatcher
import com.example.test_suites.utils.SFR
import com.example.test_suites.utils.TestAssertLogger
import com.example.test_suites.utils.UIAutomatorHelper
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import java.util.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@SFR("FCS_CKH_EXT.1/High-2", """
  FCS_CKH_EXT.1/High

  FCS_CKH.1.1/Low The TSF shall support a key hierarchy for the data encryption key(s) 
  for Low user data assets.
  
  FCS_CKH.1.2/Low The TSF shall ensure that all keys in the key hierarchy are derived and/or 
  generated according to [assignment: description of how each key in the hierarchy is derived and/or
  generated, with which key lengths and according to which standards] ensuring that the key hierarchy
  uses the DUK directly or indirectly in the derivation of the data encryption key(s) for Low user 
  data assets. 
  """)
@RunWith(AndroidJUnit4::class)
class FCS_CKH_EXT1_High2 {

  @Rule @JvmField
  var watcher: TestWatcher = ADSRPTestWatcher()
  @Rule @JvmField
  var errs: ErrorCollector = ErrorCollector()
  @Rule @JvmField
  var name: TestName = TestName()
  //Asset Log
  var a: TestAssertLogger = TestAssertLogger(name)


  lateinit var mContext: Context
  lateinit var mTargetContext: Context //Application Context
  lateinit var mDevice: UiDevice
  lateinit var mUiHelper:UIAutomatorHelper

  val TEST_PACKAGE = "com.example.test_suites"
  val PIN="1234"
  val PREF_NAME:String = "FCS_CKH_EXT_PREF"
  fun println_(line:String){
    Log.i(this.javaClass.canonicalName, line)
  }
  @Before
  fun setup()
  {
    val mDevice_ = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    mDevice = mDevice_!!
    mContext = InstrumentationRegistry.getInstrumentation().context
    mTargetContext = InstrumentationRegistry.getInstrumentation().targetContext
    mDevice.freezeRotation()

    mUiHelper = UIAutomatorHelper(mContext,mDevice_)
  }
  @After
  fun tearDown() {
    mDevice.unfreezeRotation()
  }
  @Test
  fun testHealthyCase(){

    println_("*** The test case verifies the encryption key options regarding screenlock/authentication ***")

    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }

    runBlocking {
      try {
        mUiHelper.sleepAndWakeUpDevice()
        mUiHelper.setScreenLockText("PIN", PIN)
        //Launch application
        val res = mDevice.executeShellCommand(
           "am start -n ${TEST_PACKAGE}/.EncryptionFileActivity")
        println_("Encryption File Activity Start:"+res)
        assertThat(res).isNotEqualTo("Starting")

        Thread.sleep(1000)
        mUiHelper.safeObjectClick("TEST",2000)
        Thread.sleep(5000)

        mDevice.executeShellCommand("input text ${PIN}")
        mDevice.pressEnter()
        Thread.sleep(2000)
      } finally {
        mUiHelper.resetScreenLockText(PIN)
      }
      ////////////////////////////////////////////////
      //Check preference to see result.
      //

      val pf:SharedPreferences =
        mTargetContext.getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE)
      val result_auth = pf.getString("AUTHREQUIRED","")
      val result_unlock = pf.getString("UNLOCKDEVICE","")
      pf.edit().putString("Test","test")

      println_("Expected:AUTHREQUIRED:OK,UNLOCKDEVICE:OK")
      println_("AUTHREQUIRED:"+result_auth+",UNLOCKDEVICE:"+result_unlock)

      errs.checkThat(a.Msg("Evaluate AuthRequired State in Background."),
        result_auth, CoreMatchers.`is`("OK")
      )
      errs.checkThat(a.Msg("Evaluate UnlockDevice State in Background."),
        result_unlock, CoreMatchers.`is`("OK")
      )
    }
  }

  @Test
  fun testAuthIsFailed(){
    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }
    //Check FCS_CKH_EXT1_HIGH_UNLOCK check failed if device is locked
    runBlocking {
      try {
        mUiHelper.sleepAndWakeUpDevice()
        mUiHelper.setScreenLockText("PIN", PIN)
        //Launch application

        Thread.sleep(1000)
        mUiHelper.sleepAndWakeUpDevice()

        val res = mDevice.executeShellCommand(
          "am start -n ${TEST_PACKAGE}/.EncryptionFileActivity")
        assertThat(res).isNotEqualTo("Starting")
        //not authenticate
        mUiHelper.swipeUp()
        //Sleep device -> lock screen
        Thread.sleep(2000)
        //Change
        val pf:SharedPreferences =
          mTargetContext.getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE)
        val result_auth = pf.getString("AUTHREQUIRED","OK")
        val result_unlock = pf.getString("UNLOCKDEVICE","")

        //FCS_CKH_EXT_TEST        com.example.test_suites              D  UNLOCKDEVICE:NG

        //pf.edit().putString("Test","test")
        println_("Expected:AUTHREQUIRED:OK,UNLOCKDEVICE:NG")
        println_("AUTHREQUIRED:"+result_auth+",UNLOCKDEVICE:"+result_unlock)

        errs.checkThat(a.Msg("Evaluate AuthRequired State in Background."),
          result_auth, CoreMatchers.`is`("OK")
        )
        errs.checkThat(a.Msg("Evaluate UnlockDevice State in Background."),
          result_unlock, CoreMatchers.`is`("NG")
        )

        //assertThat(result_auth).isEqualTo("OK")
        //assertThat(result_unlock).isEqualTo("NG")

      } finally {
        mDevice.executeShellCommand("input text ${PIN}")
        Thread.sleep(1000)
        mDevice.pressEnter()
        mUiHelper.resetScreenLockText(PIN)
      }

    }
  }
}