package com.example.test_suites.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request

class NetworkHelper {
  companion object {
    /**
     *
     */
    fun testHttpURLConnection(url_:String):Int {
      //"https://tls-v1-2.badssl.com:1012/"
      val url = URL(url_)
      try {
        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
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
          bufReader.close();
          inReader.close();
          ins.close();
          println(result)
        }
        return responseCode
      } catch(ex:Exception){
        ex.printStackTrace()
        return 400;
      }
    }

    /**
     *
     */
    fun testOkHttp3(url:String):Int{
      val client = OkHttpClient()

      val request:Request = Request.Builder()
        .url(url)
        .build() // defaults to GET

      val response: okhttp3.Response = client.newCall(request).execute()

      println(response.body?.string())
      return response.code
    }
  }
}