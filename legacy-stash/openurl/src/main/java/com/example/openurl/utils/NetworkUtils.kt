package com.example.openurl.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext


class NetworkUtils {
  companion object {
    /**
     *
     */
    fun testHttpURLConnection(url_:String):Int {

      val url = URL(url_)
      //System.setProperty("jdk.tls.client.protocols","TLSv1.3")

      val connection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

      /*val sc = SSLContext.getInstance("TLSv1.3","AndroidOpenSSL")
      sc.init(null, null, SecureRandom())
      sc.createSSLEngine();
      connection.setSSLSocketFactory(sc.getSocketFactory());*/
      connection.setRequestMethod("GET");
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