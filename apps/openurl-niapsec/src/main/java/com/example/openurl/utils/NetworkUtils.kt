package com.example.openurl.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import android.util.Log


class NetworkUtils {
  companion object {
    /**
     *
     */
    fun testHttpURLConnection(url_:String, p12Path: String? = null, p12Pass: String? = null, trustPath: String? = null, sslSocketFactory: javax.net.ssl.SSLSocketFactory? = null, hostnameVerifier: javax.net.ssl.HostnameVerifier? = null):Int {

      val secureUrl = com.android.certifications.niap.niapsec.net.SecureURL(url_, null)
      val connection = secureUrl.openConnection() as javax.net.ssl.HttpsURLConnection

      if (connection is HttpsURLConnection) {
          if (sslSocketFactory != null) {
              connection.sslSocketFactory = com.android.certifications.niap.niapsec.net.ValidatableSSLSocketFactory(secureUrl, sslSocketFactory)
              if (hostnameVerifier != null) {
                  connection.hostnameVerifier = hostnameVerifier
              }
              Log.d("NetworkUtils", "Using provided SSLSocketFactory")
          } else {
              var kmf: KeyManagerFactory? = null
              if (!p12Path.isNullOrBlank()) {
                  try {
                      val keyStore = KeyStore.getInstance("PKCS12")
                      val file = File(p12Path)
                      if (file.exists()) {
                          file.inputStream().use { ins ->
                              keyStore.load(ins, p12Pass?.toCharArray())
                          }
                          kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                          kmf.init(keyStore, p12Pass?.toCharArray())
                          Log.d("NetworkUtils", "Loaded P12 cert from $p12Path")
                      } else {
                          Log.e("NetworkUtils", "P12 file not found: $p12Path")
                      }
                  } catch (e: Exception) {
                      Log.e("NetworkUtils", "Failed to load P12 cert", e)
                      throw e
                  }
              }

              var tmf: javax.net.ssl.TrustManagerFactory? = null
              if (!trustPath.isNullOrBlank()) {
                  try {
                      val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                      keyStore.load(null, null)
                      val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
                      val file = File(trustPath)
                      if (file.exists()) {
                          file.inputStream().use { ins ->
                              val cert = certFactory.generateCertificate(ins)
                              keyStore.setCertificateEntry("ca", cert)
                          }
                          tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
                          tmf.init(keyStore)
                          Log.d("NetworkUtils", "Loaded trust cert from $trustPath")
                      } else {
                          Log.e("NetworkUtils", "Trust cert file not found: $trustPath")
                      }
                  } catch (e: Exception) {
                      Log.e("NetworkUtils", "Failed to load trust cert", e)
                      throw e
                  }
              }

              if (kmf != null || tmf != null) {
                  val sc = SSLContext.getInstance("TLS")
                  sc.init(kmf?.keyManagers, tmf?.trustManagers, null)
                  connection.sslSocketFactory = sc.socketFactory
                  
                  if (tmf != null) {
                      connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { hostname, session ->
                          hostname == "localhost" || hostname == "127.0.0.1"
                      }
                      Log.d("NetworkUtils", "Set custom HostnameVerifier for localhost")
                  }
              }
          }
      }
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Connection", "close");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
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
        try {
            Thread.sleep(500) // Wait for NewSessionTicket in TLS 1.3
        } catch (e: Exception) {}
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