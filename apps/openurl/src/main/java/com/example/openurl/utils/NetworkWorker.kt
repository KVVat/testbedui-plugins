package com.example.openurl.utils

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.lang.Exception
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLKeyException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

class NetworkWorker (context: Context,
                     params: WorkerParameters
) : Worker(context, params) {

  override  fun doWork(): Result {
    val url:String = inputData.getString("url")!!;
    val type:String = inputData.getString("type")!!;
    val p12Path = inputData.getString("p12path")
    val p12Pass = inputData.getString("p12pass")
    val forceTls12 = inputData.getBoolean("forceTls12", false)
    Log.d("NetworkWorker", "forceTls12: $forceTls12, type: $type [VER: 2026-04-24-05]")

    var ret: Int = 0;
    //setProgress(firstUpdate)
    setProgressAsync(Data.Builder().putString("progress","... Initialize $type").build())
    val resumption = inputData.getBoolean("resumption", false)
    if (resumption) {
      System.setProperty("http.keepAlive", "false")
    }
    var msg = "None"
    try {
      if (type.equals("http")) {
        ret = NetworkUtils.testHttpURLConnection(url, p12Path, p12Pass)
        if (resumption && ret == 200) {
          setProgressAsync(Data.Builder().putString("progress","... Attempting Resumption").build())
          // Second connection for resumption
          ret = NetworkUtils.testHttpURLConnection(url, p12Path, p12Pass)
        }
      } else if (type.equals("okhttp3")) {
        ret = NetworkUtils.testOkHttp3(url, p12Path, p12Pass, forceTls12)
      }
    } catch (ex:Exception){
      Log.e("worker@", "Network task failed: ${ex.message}", ex)
      when(ex){
        is SSLHandshakeException,is SSLKeyException, is SSLProtocolException->
          ret = 525 //SSL Handshake Failed (not standard)
        is SSLPeerUnverifiedException->
          ret = 526 //Invalid SSL Certificate(not standard)
      }
      msg = ex.message.toString()
    }

    //setProgressAsync(Data.Builder().putString("return_code","$type").build())

    if(ret == 200){
      return Result.success(Data.Builder().putString("progress","... Success(${ret})").putString("return",ret.toString()).build())
    } else {
      return Result.failure(Data.Builder().putString("progress","... Failure(${ret})\n$msg").putString("return",ret.toString()).build())
    }
  }
}


