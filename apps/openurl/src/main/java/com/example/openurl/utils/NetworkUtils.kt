package com.example.openurl.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import android.util.Log

class NetworkUtils {
  companion object {
    /**
     * 標準の HttpsURLConnection を使用した通信（元の状態に復元＋TLS1.2強制）。
     */
    fun testHttpURLConnection(url_:String, p12Path: String? = null, p12Pass: String? = null):Int {
      val url = URL(url_)
      
      // TLS 1.2 を強制するシステムプロパティ
      val connection: HttpURLConnection = url.openConnection() as HttpURLConnection

      if (connection is HttpsURLConnection) {
          if (!p12Path.isNullOrBlank()) {
              try {
                  val keyStore = KeyStore.getInstance("PKCS12")
                  val file = File(p12Path)
                  if (file.exists()) {
                      file.inputStream().use { ins ->
                          keyStore.load(ins, p12Pass?.toCharArray())
                      }
                      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                      kmf.init(keyStore, p12Pass?.toCharArray())
                      val sc = SSLContext.getInstance("TLS")
                      sc.init(kmf.keyManagers, null, null)
                      connection.sslSocketFactory = sc.socketFactory
                      Log.d("NetworkUtils", "Loaded P12 cert from $p12Path")
                  } else {
                      Log.e("NetworkUtils", "P12 file not found: $p12Path")
                  }
              } catch (e: Exception) {
                  Log.e("NetworkUtils", "Failed to load P12 cert", e)
                  throw e
              }
          }
      }
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Connection", "close");
      connection.connect();
      val responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        var ins = connection.inputStream
        var encoding = connection.getContentEncoding();
        if (null == encoding) {
          encoding = "UTF-8";
        }
        var result = StringBuffer();
        val inReader = InputStreamReader(ins, encoding);
        val bufReader = BufferedReader(inReader);
        var line: String?
        while (true) {
          line = bufReader.readLine(); //!= null
          if (line == null) break
          result.append(line)
        }
        println(result)
        bufReader.close();
        inReader.close();
        ins.close();
      }
      return responseCode
    }

    /**
     * OkHttp を使用した通信（拡張版）。
     * QUICの適用を防ぐため、およびTLS 1.2の強制機能を持つ。
     */
    fun testOkHttp3(url_:String, p12Path: String? = null, p12Pass: String? = null, forceTls12: Boolean = false):Int{
      val url = URL(url_)
      val builder = OkHttpClient.Builder()
      
      // QUICおよびHTTP/2の適用を防ぐため (HTTP/1.1を強制)
      builder.protocols(listOf(Protocol.HTTP_1_1))

      if (forceTls12) {
          val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
              .tlsVersions(TlsVersion.TLS_1_2)
              .cipherSuites(
                  "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                  "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                  "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                  "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
              )
              .build()
          builder.connectionSpecs(listOf(spec))
      }

      if (!p12Path.isNullOrBlank()) {
          try {
              val keyStore = KeyStore.getInstance("PKCS12")
              val file = File(p12Path)
              if (file.exists()) {
                  file.inputStream().use { ins ->
                      keyStore.load(ins, p12Pass?.toCharArray())
                  }
                  val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                  kmf.init(keyStore, p12Pass?.toCharArray())
                  
                  val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                  tmf.init(null as KeyStore?)
                  val trustManager = tmf.trustManagers[0] as X509TrustManager
                  
                  val sc = SSLContext.getInstance("TLS")
                  sc.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())
                  
                  builder.sslSocketFactory(sc.socketFactory, trustManager)
                  Log.d("NetworkUtils", "Loaded P12 cert from $p12Path")
              } else {
                  Log.e("NetworkUtils", "P12 file not found: $p12Path")
              }
          } catch (e: Exception) {
              Log.e("NetworkUtils", "Failed to load P12 cert", e)
              throw e
          }
      }

      val client = builder.build()
      val request = Request.Builder()
        .url(url)
        .header("Connection", "close")
        .build()

      val response = client.newCall(request).execute()
      return response.code
    }
  }
}