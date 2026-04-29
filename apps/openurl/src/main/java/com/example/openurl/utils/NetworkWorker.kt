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
    val trustPath = inputData.getString("trustpath")

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
        var sslSocketFactory: javax.net.ssl.SSLSocketFactory? = null
        var hostnameVerifier: javax.net.ssl.HostnameVerifier? = null

        // Pre-create SSLContext if we need custom trust or client cert
        if (!trustPath.isNullOrBlank() || !p12Path.isNullOrBlank()) {
            val sc = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
            var kmf: javax.net.ssl.KeyManagerFactory? = null
            if (!p12Path.isNullOrBlank()) {
                val keyStore = java.security.KeyStore.getInstance("PKCS12")
                java.io.File(p12Path).inputStream().use { ins ->
                    keyStore.load(ins, p12Pass?.toCharArray())
                }
                kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(keyStore, p12Pass?.toCharArray())
            }
            
            var tmf: javax.net.ssl.TrustManagerFactory? = null
            if (!trustPath.isNullOrBlank()) {
                val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
                keyStore.load(null, null)
                val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
                java.io.File(trustPath).inputStream().use { ins ->
                    val cert = certFactory.generateCertificate(ins)
                    keyStore.setCertificateEntry("ca", cert)
                }
                tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(keyStore)
            }
            
            sc.init(kmf?.keyManagers, tmf?.trustManagers, null)
            sslSocketFactory = sc.socketFactory
            
            if (tmf != null) {
                hostnameVerifier = javax.net.ssl.HostnameVerifier { hostname, session ->
                    hostname == "localhost" || hostname == "127.0.0.1"
                }
            }
        }

        ret = NetworkUtils.testHttpURLConnection(url, p12Path, p12Pass, trustPath, sslSocketFactory, hostnameVerifier)
        if (resumption && ret == 200) {
          setProgressAsync(Data.Builder().putString("progress","... Attempting Resumption").build())
          // Second connection for resumption - REUSE the sslSocketFactory!
          ret = NetworkUtils.testHttpURLConnection(url, p12Path, p12Pass, trustPath, sslSocketFactory, hostnameVerifier)
        }
      } else if (type.equals("okhttp3")) {
        ret = NetworkUtils.testOkHttp3(url)
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


