package com.example.test_suites.utils

import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.text.MessageFormat
import java.time.LocalDateTime

class ADSRPTestWatcher():TestWatcher() {
  private lateinit var deviceType:String
  private lateinit var deviceSerial:String
  private lateinit var system:String
  private lateinit var osversion:String
  override fun starting(desc: Description?) {
    println(MessageFormat.format("==========================================\n[Test Start] : {0} on {1}", desc, LocalDateTime.now()))
    //reffer android api

    this.osversion = Build.VERSION.RELEASE
    this.system = Build.DISPLAY
    this.deviceType = Build.TYPE
    this.deviceSerial =  Settings.Secure.ANDROID_ID//ro.boot.serialno

    //this.deviceType= Build.MODEL


  }

  override fun succeeded(desc: Description?) {
    println(MessageFormat.format("[Test Succeeded] : {0}", desc))
  }

  override fun failed(e: Throwable, desc: Description?) {
    System.err.println(
      MessageFormat.format(
        "[Test Failed] : {0} \r\n*** Exception : {1}.", desc, e.message
      )
    )
  }


  override fun finished(desc: Description?) {
    println(MessageFormat.format("[Test Finished] : {0}", desc))
    //postprocess

    val myClassKClass = desc!!.testClass
    //myClassKClass.
    var sfr = myClassKClass.getAnnotation(SFR::class.java)
    if (sfr == null) {
      sfr = SFR("dummy", "dummy")
    }
    //JSONObject
    val testspec =
      JSONObject.quote("{'SFR.name':'${sfr.title.trim()}','SFR.desc':'${sfr.description.trim()}','device':'$deviceType','osversion':'$osversion','system':'$system','deviceserial':'$deviceSerial'}")
    println("[TestSpec] : $testspec")
  }
}