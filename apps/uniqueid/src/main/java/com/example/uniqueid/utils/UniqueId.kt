package com.example.uniqueid.utils

import android.content.Context
import android.media.MediaDrm
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID


class UniqueId {
  companion object {



    data class ShellResult(val output:String,val error:String,val ret:Int)
    /**
     * Invokes and logs the stdout / stderr of the provided shell `command`, returning the
     * exit code from the command.
     */
    protected fun runShellCommand(command: String ): ShellResult {
      return try {
        Log.d("UniqueID", "Attempting to run command "+command)
        val process = Runtime.getRuntime().exec(command)

        val returnCode = process.waitFor()
        //Log.d("UniqueID", "ret>"+returnCode+","+process.inputStream)
        val reader = InputStreamReader(process.inputStream);
        val stdout = BufferedReader(reader);
        val stderr = BufferedReader(
          InputStreamReader(process.errorStream)
        )
        val stdoutBuilder = StringBuilder()
        var line: String?;
        //Log.d("UniqueID", "ret>"+stdout.toString()+","+stderr.toString())
        while (stdout.readLine().also { line = it } != null) {
          stdoutBuilder.append(line!!.trimIndent())
          stdoutBuilder.append("\n")
        }
        val stderrBuilder = StringBuilder()
        while (stderr.readLine().also { line = it } != null) {
          stderrBuilder.append(line!!.trimIndent())
        }
        Log.d("UniqueID", "Process return code: $returnCode")
        Log.d("UniqueID", "Process stdout: $stdoutBuilder")
        Log.d("UniqueID", "Process stderr: $stderrBuilder")
        ShellResult(stdoutBuilder.toString(),stderrBuilder.toString(),returnCode)
      } catch (e: Throwable) {
        throw e
      }
    }

    fun invokeReflectionCall(
      targetClass: Class<*>, methodName: String?,
      targetObject: Any?, parameterClasses: Array<Class<*>?>, vararg parameters: Any?,
    ): Any? {
      return try {
        val method = targetClass.getMethod(methodName, *parameterClasses)
        method.invoke(targetObject, *parameters)
      } catch (e: ReflectiveOperationException) {
        val cause = e.cause
        if (cause is SecurityException) {
          throw (cause as SecurityException?)!!
        } else {
          throw e;
        }
      }
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    //Simple Unique ID
    fun generateUuid():String{
      return UUID.randomUUID().toString()
    }

    fun getIMEI3():String{
      return ""
      /*return try {
          val r = Build.getSerial();
        r;
      } catch (ex:Exception){
        ""
      }*/
    }
    fun getIMEI1():String{
      try {
        //Shell
        val r:ShellResult = runShellCommand("service call iphonesubinfo 4 i64 0".trimIndent())
        if(r.ret == 0) {
          if (r.output.startsWith("Result: Parcel(")) {
            val lines:List<String> = r.output.split("\n");
            val result = StringBuffer();
            for(line:String in lines){
              val fi = line.indexOf("'");
              if(fi != -1) {
                var str = line.subSequence(fi, line.indexOf("'", fi + 1))
                str = str.replace("\'|\\.".toRegex(),"")
                result.append(str)
              }
            }
            val res = result.toString();
            if(res.matches("^[0-9]*$".toRegex())){
                return res;
            } else {
              Log.d("UniqueId","Error:"+result)
              return ""
            }
          } else {
            return "";
          }
        } else {
          return ""
        }
      } catch (e:java.lang.Exception){
        e.printStackTrace()
        return "";
      }
    }

    fun getIMEI2(context: Context):String{
      //Telephony Manager
      try {
        val tm: TelephonyManager =
          context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager;
        return tm.deviceId;
      } catch (e:SecurityException){
        return ""
      }
    }

    //
    fun getWidevineId():String{
      val widevineUuid = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
      val ba = MediaDrm(widevineUuid).getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
      return ba.toHex()
    }
    // https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html
    fun getAndroidId(ctx: Context):String{
      return Settings.Secure.getString(ctx.getContentResolver(), Settings.System.ANDROID_ID)
    }
    //
    suspend fun getAdId(applicationContext:Context): String {
      return withContext(Dispatchers.Default) {
        try {
          AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id!!
        } catch (exception: Exception) {
          "" // there still can be an exception for other reasons but not for thread issue
        }
      }
    }
  }
}