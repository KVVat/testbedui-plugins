package org.example.plugin.ui

import org.example.project.adb.rules.AdbDeviceRule
import org.example.plugin.utils.UIAutomatorSession
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class UIAutomatorTest {
    @get:Rule
    val adb = AdbDeviceRule()
    //private val client = adb.adb

    @Test
    fun testUIAutomator() {

        UIAutomatorSession(adb,"com.android.settings").run {
            runBlocking {
//                copyxml(Paths.get("../results/out.xml"))
//                freezeRotation()
//                turnon()
//                showSettings("security")
//                delay(500)
//                listAllClickables()
//                unfreezeRotation()
            }
            //copyxml(Paths.get("../results/out.xml"))

            //listClickable
            //com.android.settings
            //Find View resource-id =
            //com.android.settings:id/lockPattern
            //Get Bounds

            //adb shell input touchscreen swipe 930 880 930 380 #Swipe UP
        }

    }

}