package com.example.directboot


import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.directboot.databinding.ActivityMainBinding

//The module simply record Unique Id to the configuration file
class MainActivity : AppCompatActivity() {
  private val TAG:String = "FCS_CKH_EXT_TEST"
  private lateinit var binding:ActivityMainBinding
  private lateinit var receiver : BootReceiver // For Checking DirectBoot

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater);
    setContentView(binding.root)
    receiver = BootReceiver()
    LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
      receiver,
      IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED)
    )
    LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
      receiver,
      IntentFilter(Intent.ACTION_BOOT_COMPLETED)
    )
    IntentFilter(Intent.ACTION_BOOT_COMPLETED).also {
      registerReceiver(receiver,it)
    }
    IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED).also {
      registerReceiver(receiver,it)
    }
  }

  override fun onStart() {
    super.onStart()

    val sharedPref = storageContext().getSharedPreferences(TAG, Context.MODE_PRIVATE)
    sharedPref.edit().putString(TAG,"Success").apply()
    Thread.sleep(500)

    Log.d(TAG, "Booted");

  }
  fun storageContext():Context{
    var storageContext:Context;
    if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // All N devices have split storage areas, but we may need to
      // move the existing preferences to the new device protected
      // storage area, which is where the data lives from now on.
      var deviceContext:Context = applicationContext.createDeviceProtectedStorageContext();
      if (!deviceContext.moveSharedPreferencesFrom(applicationContext,
          TAG)) {
        Log.w("storageContext", "Failed to migrate shared preferences.");
      }
      Log.d("storageContext","save preference to device context");
      storageContext = deviceContext;
    } else {
      storageContext = applicationContext;
    }
    return storageContext;
  }


}