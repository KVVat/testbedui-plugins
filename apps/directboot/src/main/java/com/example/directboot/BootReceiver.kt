package com.example.directboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver: BroadcastReceiver()
{
    private val TAG: String = "FCS_CKH_EXT_TEST"

    override fun onReceive(appContext: Context?, intent: Intent?) {
        Log.d("DirectBoot","received!"+intent?.action.toString())
        //Log.d("", "directboot Event Received");

        val intentName = intent?.action.toString()
        if (intentName == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            Log.d("", "LOCKED_BOOT_COMPLETED")
            val desContext: Context =
                appContext!!.createDeviceProtectedStorageContext()// Access appDataFilename that lives in device encrypted storage
            desContext.moveSharedPreferencesFrom(appContext, TAG)
            var result_des: String? = "Failed"
            var result_app: String? = "Failed"
            try {
                val sharedPref_des = desContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                result_des = sharedPref_des.getString(TAG, "Failed")
            } catch (ex: IllegalStateException) {
                //Panic
                ex.printStackTrace()
            }
            try {
                val sharedPref_app = appContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                result_app = sharedPref_app.getString(TAG, "Failed")
            } catch (ex: IllegalStateException) {
                //expected
                ex.printStackTrace()
            }
            Log.d(TAG, "des=$result_des,ces=$result_app")

        } else if (intentName == "android.intent.action.BOOT_COMPLETED") {
            Log.d("", "BOOT_COMPLETED")
            //Check Credential Encrypted Storage (CES)
            val desContext: Context =
                appContext!!.createDeviceProtectedStorageContext()// Access appDataFilename that lives in device encrypted storage
            desContext.moveSharedPreferencesFrom(appContext, TAG)
            var result_des: String? = "Failed"
            var result_app: String? = "Failed"
            try {
                val sharedPref_des = desContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                result_des = sharedPref_des.getString(TAG, "Failed")
            } catch (ex: IllegalStateException) {
                //Panic
                ex.printStackTrace()
            }
            try {
                val sharedPref_app = appContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                result_app = sharedPref_app.getString(TAG, "Failed")
            } catch (ex: IllegalStateException) {
                //expected
                ex.printStackTrace()
            }
            Log.d(TAG, "des=$result_des,ces=$result_app")
        }
    }

}
