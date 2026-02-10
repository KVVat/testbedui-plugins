package com.example.test_suites

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.test_suites.utils.ADSRPTestWatcher
import com.example.test_suites.utils.SFR
import com.example.test_suites.utils.TestAssertLogger
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.time.LocalDateTime
import javax.security.cert.CertificateException
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is` as Is
import org.hamcrest.CoreMatchers.`not` as Not
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@SFR("FCS_CKH_EXT.1/High", """
  FCS_CKH_EXT.1/High

  FCS_CKH.1.1/Low The TSF shall support a key hierarchy for the data encryption key(s) 
  for Low user data assets.
  
  FCS_CKH.1.2/Low The TSF shall ensure that all keys in the key hierarchy are derived and/or 
  generated according to [assignment: description of how each key in the hierarchy is derived and/or
  generated, with which key lengths and according to which standards] ensuring that the key hierarchy
  uses the DUK directly or indirectly in the derivation of the data encryption key(s) for Low user 
  data assets. 
  """)
@RunWith(AndroidJUnit4::class)
class FCS_CKH_EXT1_High {
  //https://github.com/stravag/android-sample-biometric-prompt/blob/master/app/src/main/java/ch/ranil/sample/android/biometricpromptsample/BiometricPromptManager.kt
  @Rule @JvmField
  var watcher: TestWatcher = ADSRPTestWatcher()
  @Rule @JvmField
  var errs: ErrorCollector = ErrorCollector()
  @Rule @JvmField
  var name: TestName = TestName()

  //Asset Log
  var a: TestAssertLogger = TestAssertLogger(name)

  private lateinit var norm_enc_data: SharedPreferences
  val PREF_NAME:String = "EncryptedSharedPref"

  lateinit var appContext:Context
  lateinit var keyNormal:MasterKey
  lateinit var keyUnlockDeviceTest:MasterKey
  fun println_(line:String){
    Log.d(this.javaClass.canonicalName,line)
  }
  @Before
  fun setup()
  {
    appContext = InstrumentationRegistry.getInstrumentation().targetContext

    keyNormal = MasterKey.Builder(appContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()

    keyUnlockDeviceTest = MasterKey.Builder(appContext).setKeyGenParameterSpec(
      keyGenParameterSpec("_androidx_security_master_key_",false,true)
    ).build()

    norm_enc_data = EncryptedSharedPreferences
      .create(appContext,
        PREF_NAME,
        keyNormal,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )

    val fdelete: File = File(appContext.filesDir, "my_sensitive_loremipsum.txt")
    if (fdelete.exists()) {fdelete.delete()}

    println_("** A Junit test case for FCS_CKH_EXT1_High started on "+ LocalDateTime.now()+" **")

  }
  fun keyGenParameterSpec(keyNameAlias:String,authRequired:Boolean,unlockDeviceRequired:Boolean): KeyGenParameterSpec {
    try {
      return KeyGenParameterSpec.Builder(keyNameAlias,
                                         KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setKeySize(256).setEncryptionPaddings(
          KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUnlockedDeviceRequired(unlockDeviceRequired)
        .setUserAuthenticationRequired(authRequired) // Require that the user has unlocked in the last 30 seconds
        .setUserAuthenticationValidityDurationSeconds(30)
        .build()
      //keyGenerator.generateKey()
    } catch (e: NoSuchAlgorithmException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    } catch (e: NoSuchProviderException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    } catch (e: InvalidAlgorithmParameterException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    } catch (e: KeyStoreException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    } catch (e: CertificateException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    } catch (e: IOException) {
      throw java.lang.RuntimeException("Failed to create a symmetric key", e)
    }
  }
  @Test
  fun testEncryptedSharedPreference(){
    runBlocking {
      checkEncryptedSharedPreference(norm_enc_data,PREF_NAME)
    }
  }

  fun checkEncryptedSharedPreference(data:SharedPreferences,prefName:String)
  {
    val sampleString = "The quick brown fox jumps over the lazy dog"

    println_("> Generate encrypted Shared Preferences")

    val editor: SharedPreferences.Editor = data.edit()

    editor.putInt("IntTest", 65535)
    editor.putBoolean("BooleanTest", true)
    editor.putString("StringTest", sampleString)
    editor.apply()
    //check availability
    println_("Check availavility of values")

    val intSaved = data.getInt("IntTest", 1)
    //assertEquals(65535,intSaved)
    errs.checkThat(a.Msg("Evaluate Encrypted integer value matches"),
                   intSaved,Is(65535))


    val boolSaved = data.getBoolean("BooleanTest", false)
    errs.checkThat(a.Msg("Evaluate Encrypted boolean value matches"),
                   boolSaved,Is(true))


    val strSaved = data.getString("StringTest", "")
    errs.checkThat(a.Msg("Evaluate Encrypted String value matches"),
                   strSaved,Is(sampleString))

    //
    println_("Check all values in the shared preference are encrypted.")
    loadSharedPrefs(prefName)
  }

  @Test
  fun testEncryptedFile() {
    val fileToWrite = "my_sensitive_loremipsum.txt"

    val isLoremIpsum: InputStream = appContext.resources.openRawResource(
      appContext.resources.getIdentifier("loremipsum",
                                         "raw", appContext.packageName))
    val content = isLoremIpsum.bufferedReader().use(BufferedReader::readText)

    val fTarget: File = File(appContext.filesDir, fileToWrite)

    println_("Generate a encrypted file with EncryptedFile Class")
    val encryptedFile = EncryptedFile.Builder(
      appContext,
      fTarget,
      keyNormal,
      EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    //Write loaded file with EncryptedFile class
    try {
      val outputStream: FileOutputStream = encryptedFile.openFileOutput()
      outputStream?.apply {
        write(content.toByteArray(Charset.forName("UTF-8")))
        flush()
        close()
      }

    } catch (ex: IOException) {
      throw RuntimeException("IOException")
    }
    //Check Availability
    errs.checkThat(a.Msg("Check availability of the encrypted file"),
                   fTarget.exists(),Is(true))

    val original:String
    encryptedFile.openFileInput().use { fileInputStream ->
      try {
        val sb = StringBuilder()
        val br = BufferedReader(InputStreamReader(fileInputStream) as Reader?)
        br.readLine()
          .forEach {
            sb.append(it)
          }
        br.close()
        original = sb.toString()
      } catch (ex: Exception) {
        // Error occurred opening raw file for reading.
        throw RuntimeException("IOException")
      } finally {
        fileInputStream.close()
      }
    }
    //Check the file is encrypted (Read the file with BufferedReader)
    val fTargetStream = FileInputStream(fTarget)
    try {
      val sb = StringBuilder()
      val br = BufferedReader(InputStreamReader(fTargetStream) as Reader?)
      br.readLine()
        .forEach {
          sb.append(it)
        }
      br.close()
      val encrypted = sb.toString()
      println_("Check the file is encrypted.(means they are not identical)")

      println_("===original content===")
      println_(original)
      println_("===encrypeted content===")
      println_(encrypted)

      errs.checkThat(a.Msg("Check the file is encrypted.it means these are not identical"),
                     original,Is(Not(encrypted)))

    } catch (ex: Exception) {
      // Error occurred opening raw file for reading.
      throw RuntimeException("IOException")
    } finally {
      fTargetStream.close()
    }
  }

  fun loadSharedPrefs(vararg prefs: String?) {
    // Logging messages left in to view Shared Preferences. I filter out all logs except for ERROR; hence why I am printing error messages.
    for (pref_name in prefs) {
      val preference: SharedPreferences = appContext.getSharedPreferences(pref_name, MODE_PRIVATE)
      for (key in preference.all.keys) {
        val res = String.format("Shared Preference : %s - %s", pref_name, key)+
              preference.getString(key, "error")!!
        println_(res)
      }
    }
  }
}