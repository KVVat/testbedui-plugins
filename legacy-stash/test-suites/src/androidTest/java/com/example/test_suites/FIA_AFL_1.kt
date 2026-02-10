package com.example.test_suites

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.test_suites.utils.ADSRPTestWatcher
import com.example.test_suites.utils.TestAssertLogger
import com.example.test_suites.utils.UIAutomatorHelper
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)//Execute methods in order of appearance
class FIA_AFL_1 {


  @Rule
  @JvmField
  var watcher: TestWatcher = ADSRPTestWatcher()
  @Rule
  @JvmField
  var errs: ErrorCollector = ErrorCollector()
  @Rule
  @JvmField
  var name: TestName = TestName()

  //Asset Log
  var a: TestAssertLogger = TestAssertLogger(name)

  private val PIN = "1234"
  private val PASSWORD = "aaaa"

  private lateinit var mDevice: UiDevice
  private lateinit var mContext: Context
  private lateinit var mTargetContext: Context
  lateinit var mUiHelper:UIAutomatorHelper
  fun println_(line:String){
    Log.i(this.javaClass.canonicalName,line)
  }
  //Pattern Pixel 5e :
  /*private var PATS:Map<String,Array<Point>> = mapOf(
    Pair("sdk_gphone64_x86_64-33",
         arrayOf(Point(230, 1800), Point(230, 850),Point(512,1500),Point(880, 1800))
    ),
    Pair("Pixel 5a-33",
         arrayOf(Point(230, 1800), Point(230, 850),Point(512,1500),Point(880, 1800))
    ),

  )

  private var PAT:Array<Point> = arrayOf(Point(230, 1800),
    Point(230, 850),Point(512,1500),Point(880, 1800));
  */

  @Before
  fun setUp() {
    val mDevice_ = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    mDevice = mDevice_!!

    mContext = InstrumentationRegistry.getInstrumentation().context
    mTargetContext = InstrumentationRegistry.getInstrumentation().targetContext


    mDevice.freezeRotation()
    mUiHelper = UIAutomatorHelper(mContext,mDevice_)
    //rintln_("** A Junit test case for FIA_AFL_1 started on "+ LocalDateTime.now()+" **")

  }

  @After
  fun tearDown() {
    mDevice.unfreezeRotation()
  }


  @Test
  fun T01_testPINLockSuccess(){
    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }
    try {
      println_("Test succeed to set PIN lock.")
      mUiHelper.sleepAndWakeUpDevice()
      mUiHelper.setScreenLockText("PIN", PIN)
      //Launch application
      mDevice.waitForIdle()
      mUiHelper.sleepAndWakeUpDevice()//LockScreen

      println_("Test succeed to unlock screen with PIN.")
      Thread.sleep(1000)
      mUiHelper.swipeUp()
      mDevice.executeShellCommand("input text ${PIN}")
      Thread.sleep(1000)
      mDevice.pressEnter()
      Thread.sleep(1000)

    } finally {
      println_("Try to reset the screen lock")
      mUiHelper.resetScreenLockText(PIN)
    }
    //if the operation above fails, there must be a problem.
    assert(!mUiHelper.isLockScreenEnbled())
  }

  @Test
  fun T02_testPINLockFailure(){
    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }
    try {
      println_("Test succeed to set PIN lock.")
      mUiHelper.sleepAndWakeUpDevice()
      mUiHelper.setScreenLockText("PIN", PIN)
      //Launch application
      mDevice.waitForIdle()
      mUiHelper.sleepAndWakeUpDevice()//LockScreen

      Thread.sleep(1000)
      mUiHelper.swipeUp()

      for (i in 0..4) {
        println_("Test failure to unlock screen with PIN({$i+1} attempt).")
        mDevice.executeShellCommand("input text 0000")
        Thread.sleep(1000)
        mDevice.pressEnter()
        Thread.sleep(1000)
      }
      //if it fails 5 times, 30 sec trial delay is applied
      println_("Wait 30 sec for retry.")
      mUiHelper.safeObjectClick("OK",1000)

    } finally {
      Thread.sleep(30 * 1000)//wait 30 sec
      println_("Try to reset the screen lock.")
      mUiHelper.sleepAndWakeUpDevice()//LockScreen
      //Thread.sleep(1000);
      mUiHelper.swipeUp()
      //Thread.sleep(1000);
      //Need to unlock screen
      mDevice.executeShellCommand("input text ${PIN}")
      Thread.sleep(1000)
      mDevice.pressEnter()
      Thread.sleep(1000)
      mUiHelper.resetScreenLockText(PIN)
    }
    //if the operation above fails, there must be a problem.
    assert(!mUiHelper.isLockScreenEnbled())
  }

  @Test
  fun T11_testPassLockSuccess(){
    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }
    try {
      println_("Test succeed to set Password lock.")
      mUiHelper.sleepAndWakeUpDevice()
      mUiHelper.setScreenLockText("Password", PASSWORD)
      //Launch application
      mDevice.waitForIdle()
      mUiHelper.sleepAndWakeUpDevice()//LockScreen
      println_("Test succeed to unlock screen with Password.")
      Thread.sleep(1000)
      mUiHelper.swipeUp()
      mDevice.executeShellCommand("input text ${PASSWORD}")
      Thread.sleep(1000)
      mDevice.pressEnter()
      Thread.sleep(1000)

    } finally {
      println_("Try to reset the screen lock")
      mUiHelper.resetScreenLockText(PASSWORD)
    }
    //if the operation above fails, there must be a problem.
    assert(!mUiHelper.isLockScreenEnbled())
  }

  @Test
  fun T12_testPassLockFailure(){
    if(mUiHelper.isLockScreenEnbled()){
      println_("*** It requires to disable screen lock to run this test ***")
      assert(false)
    }
    try {
      println_("Test succeed to set Password lock.")
      mUiHelper.sleepAndWakeUpDevice()
      mUiHelper.setScreenLockText("Password", PASSWORD)
      //Launch application
      mDevice.waitForIdle()
      mUiHelper.sleepAndWakeUpDevice()//LockScreen

      Thread.sleep(1000)
      mUiHelper.swipeUp()
      for (i in 0..4) {
        println_("Test failure to unlock screen with PIN({$i+1} attempt).")
        mDevice.executeShellCommand("input text bbbb")
        Thread.sleep(1000)
        mDevice.pressEnter()
        Thread.sleep(1000)
      }
      //if it fails 5 times, 30 sec trial delay is applied
      println_("Wait 30 sec for retry.")
      mUiHelper.safeObjectClick("OK",1000)

    } finally {
      println_("Try to reset the screen lock")
      Thread.sleep(30 * 1000)//wait 30 sec
      mUiHelper.sleepAndWakeUpDevice()//LockScreen
      Thread.sleep(1000)
      mUiHelper.swipeUp()
      Thread.sleep(1000)
      //Need to unlock screen
      mDevice.executeShellCommand("input text ${PASSWORD}")
      Thread.sleep(1000)
      mDevice.pressEnter()
      Thread.sleep(1000)
      mUiHelper.resetScreenLockText(PIN)
    }
    //if the operation above fails, there must be a problem.
    assert(!mUiHelper.isLockScreenEnbled())

  }

  /*
  @Test
  fun testPatternSuccess(){
    if(mUiHelper.isLockScreenEnbled()){
      println("*** It requires to disable screen lock to run this test ***");
      assert(false)
    }
  }
  @LargeTest
  fun testPatternFailure(){
    if(mUiHelper.isLockScreenEnbled()){
      println("*** It requires to disable screen lock to run this test ***");
      assert(false)
    }
  }
  */

  /*
  fun T31_testSetupPatternLock() {
    assert(!isLockScreenEnbled())
    runBlocking {
      sleepAndWakeUpDevice()
      launchSettings(Settings.ACTION_SECURITY_SETTINGS);
      swipeUp()
      Thread.sleep(1000);
      safeObjectClick("Screen lock",2000)
      safeObjectClick("Pattern",2000)
      for(i in 0..1) {
        mDevice.swipe(PAT,4);
        //mDevice.pressEnter()
        Thread.sleep(2000);
        if(i == 0){
          safeObjectClick("Next",2000)
        } else {
          safeObjectClick("Confirm",2000)
        }
        Thread.sleep(2000);
      }
      safeObjectClick("Done",2000)
    }
  }

  @Test
  fun T32_unlockScreenPatternSuccess() {

    assert(isLockScreenEnbled())
    runBlocking {
      sleepAndWakeUpDevice()
      mDevice.waitForIdle()
      Thread.sleep(1000);
      swipeUp()
      mDevice.swipe(PAT,4);
      Thread.sleep(2000);
    }
    println(isLockScreenEnbled());
  }

  fun setupLockNone(passInput:String) {
    //to start this test you should disable screen lock first
    //assert(isLockScreenEnbled())
    runBlocking {
      sleepAndWakeUpDevice()
      mDevice.waitForIdle()
      Thread.sleep(1000);
      swipeUp()
      Thread.sleep(1000);
      mDevice.executeShellCommand("input text ${passInput}")
      mDevice.pressEnter()
      Thread.sleep(1000);
      launchSettings(Settings.ACTION_SECURITY_SETTINGS);
      Thread.sleep(1000);
      swipeUp()
      Thread.sleep(1000);
      safeObjectClick("Screen lock",2000)
      mDevice.executeShellCommand("input text ${passInput}")
      mDevice.pressEnter()
      Thread.sleep(1000);
      safeObjectClick("None",2000)
      safeObjectClick("Delete",2000)
    }
  }
  */

}