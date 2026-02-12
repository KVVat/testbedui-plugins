package com.example.openurl

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.openurl.databinding.ActivityMainBinding
import com.example.openurl.utils.NetworkWorker

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  var url: String? = null
  var type: String? = "http"
  //private var workManager: WorkManager? =null;
  var TAG = "worker@"

  val workManager = WorkManager.getInstance(this)
  lateinit var workRequest: OneTimeWorkRequest
  // = OneTimeWorkRequest.from(NetworkWorker::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    url = intent.getStringExtra("openurl")
    val type_ = intent.getStringExtra("type")
    if(!type_.isNullOrBlank()){
      type = type_
    }
    /*
    url = "https://expired.badssl.com/"
    url = "https://wrong.host.badssl.com/"
    url = "https://self-signed.badssl.com/"
    url = "https://revoked.badssl.com/"
    url = "https://tls-v1-0.badssl.com:1010/"
    */
    if(url !== null){
      binding.mainText.text = url
      val data = Data.Builder().putString("url",url).putString("type",type).build()

      workRequest = OneTimeWorkRequest.Builder(NetworkWorker::class.java).setInputData(data).build()
      WorkManager.getInstance(applicationContext)
        // requestId is the WorkRequest id
        .getWorkInfoByIdLiveData(workRequest.id)
        .observe(this, Observer { workInfo: WorkInfo? ->
          Log.d(TAG,workInfo.toString())
          if (workInfo != null) {
            val progress = workInfo.progress
            var value = progress.getString("progress")
            if(value.isNullOrBlank()){
              value = workInfo.outputData.getString("progress")
            }
            // Do something with progress information
            if(!value.isNullOrBlank()) {
              binding.resultText.text =
                binding.resultText.text.toString() + "\n" + value

              val returnValue = workInfo.outputData.getString("return")
              if(!returnValue.isNullOrBlank()) {
                Log.d(TAG + "return", "" + returnValue.toString())
              }
            }
          }
        })
      binding.resultText.text = "... Launch Work Manager"
      workManager.enqueue(workRequest)
    } else {
      binding.resultText.text = "... Url is not specified"
    }
  }

}