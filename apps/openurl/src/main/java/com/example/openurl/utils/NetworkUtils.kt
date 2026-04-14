package com.example.openurl.utils

import okhttp3.OkHttpClient
import okhttp3.Request
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
import android.util.Log


class NetworkUtils {
  companion object {
    /**
     *
     */
    fun testHttpURLConnection(url_:String, p12Path: String? = null, p12Pass: String? = null):Int {

      val url = URL(url_)
      //System.setProperty("jdk.tls.client.protocols","TLSv1.3")

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
     *
     */
    fun testOkHttp3(url:String):Int{
      val client = OkHttpClient()

      val request: Request = Request.Builder()
        .url(url)
        .build() // defaults to GET

      val response: okhttp3.Response = client.newCall(request).execute()

      //println(response.body?.string())
      return response.code
    }
  }
}