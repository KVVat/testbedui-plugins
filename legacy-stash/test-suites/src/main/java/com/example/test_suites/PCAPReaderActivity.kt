package com.example.test_suites

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.pkts.PacketHandler
import io.pkts.Pcap
import java.io.File


class PCAPReaderActivity : AppCompatActivity() {
  private val TAG: String = "FTP_ITC_EXT_TEST"
  private val REQUEST_EXTERNAL_STORAGE=1;
  private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

  fun runProc(){
    val dir = Environment.getExternalStorageDirectory()
    val yourFile = File(dir, "Download/PCAPdroid/traffic.pcap")
    if(yourFile.exists()){
      Log.d(TAG, yourFile.absolutePath);
      var pcap = Pcap.openStream(yourFile)
      pcap.loop(PacketHandler {
        Log.d(TAG,it.name);
        true;
      })
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    val startForResult =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
        if (result?.resultCode == Activity.RESULT_OK) {
          result.data?.let { _ ->
            //val value = data.toString();//data.getIntExtra(SubActivity.KEY_VALUE, 0)
            //Toast.makeText(this, "$value", Toast.LENGTH_LONG).show()
            //data.dataString
          }
        }
      }
    startForResult.launch(intent)
    /*
    prefSetting = PreferenceManager.getDefaultSharedPreferences(this)
    path_button.setOnClickListener {
      // Intent 起動
      startActivityForResult(intent, REQUEST_CODE)
    }*/

    /*
    val permission =
      ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

    if (permission != PackageManager.PERMISSION_GRANTED) {
      // We don't have permission so prompt the user
      ActivityCompat.requestPermissions(
        this,
        PERMISSIONS_STORAGE,
        REQUEST_EXTERNAL_STORAGE
      )
    } else {
      Log.d(TAG,"permitted")
      runProc()
    }
    */
    /*val extras = intent.getStringExtra("")
    if (extras != null) {
      // Step 6: Get the data out of the Bundle
      items = extras.getString()
    } else {
      items = ArrayList<Listitem>()
    }*/

  }
}