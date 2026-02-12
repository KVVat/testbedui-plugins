package org.example.assets.target

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import org.example.assets.target.AppDatabase
import org.example.assets.target.User
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//The module simply record Unique Id to the configuration file
class PrepareActivity : AppCompatActivity() {

  val TAG:String = "FDP_ACC_1_TEST";

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    //Prepare Data
    getPrefValueOrWrite("SampleValue","Success");
    //preparePrivateFileForTest("testfile.txt");
    //prepareMediaFileForTest();
    CoroutineScope(Dispatchers.IO).launch {
      prepareRoomDatabaseForTest();
    }
  }

  fun prepareRoomDatabaseForTest(){
    //
    val db = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java, "database-name"
    ).build()
    //
    if(db.userDao().loadAllByIds(intArrayOf(1)).size == 0) {
      db.userDao().insertAll(arrayOf(
        User(1, "Sample", "User"),
        User(2, "Tony", "Stark"),
        User(3, "Bruce", "Wayne"),
        User(4, "Clark", "Kent")))
    }
    Log.d(TAG,
          "Successfully wrote database")
  }

  fun prepareMediaFileForTest(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val contentResolver = application.contentResolver
      val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
          )
        } else {
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
      //if the file already exists, do nothing
      val cursor = contentResolver.query(collection,
                                         arrayOf(MediaStore.Video.Media._ID,MediaStore.Images.Media.DISPLAY_NAME),
                                         "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(TAG+".jpg"),
                                         null);
      if (cursor != null && cursor.moveToFirst()) {
        cursor.close()
        return
      }
      //
      val photoContentValues = ContentValues()
      photoContentValues.put(MediaStore.Images.Media.DISPLAY_NAME, TAG)
      photoContentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
      photoContentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/")
      photoContentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
      val collectionUri = MediaStore.Images.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY)
      val photoUri = contentResolver.insert(collectionUri, photoContentValues)
      try {
        resources.openRawResource(R.raw.test_image).use { inputStream ->
          contentResolver.openOutputStream(
            photoUri!!).use { outputStream ->
            val bytes = ByteArray(2048)
            while (inputStream.read(bytes) != -1) {
              outputStream!!.write(bytes)
            }
            Log.d(TAG,
                  "Successfully wrote file to pictures directory")
          }
        }
      } catch (e: IOException) {
        Log.e(TAG,
              "Caught an exception copying file:",
              e)
      }
      photoContentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
      contentResolver.update(photoUri!!, photoContentValues, null, null)
    } else {
      Log.d(TAG,
            "Android Q is required to execute")
    }
  }

  fun preparePrivateFileForTest(fileToWrite:String){

    val isLoremIpsum: InputStream = applicationContext.resources.openRawResource(
      applicationContext.resources.getIdentifier("loremipsum",
                                         "raw", applicationContext.packageName));
    val content = isLoremIpsum.bufferedReader().use(BufferedReader::readText)

    val fTarget= File(applicationContext.getFilesDir(), fileToWrite)
    //Write loaded file with EncryptedFile class
    try {
      val outputStream: FileOutputStream? = FileOutputStream(fTarget);//
      outputStream?.apply {
        write(content.toByteArray(Charset.forName("UTF-8")))
        flush()
        close()
      }
      Log.d(TAG,
            "Successfully wrote a test file into privatedir named testfile.txt")

    } catch (ex: IOException) {
      throw RuntimeException("IOException")
    }
  }

  fun getPrefValueOrWrite(label:String,value:String):String{
    val sharedPref = getSharedPreferences(TAG, Context.MODE_PRIVATE)
    val ret = sharedPref.getString(label,"")
    if(ret==""){
      if(!ret.equals(value)){
        Log.d(TAG, "New API Value=>"+value+" assigned.")
      } else {
        Log.d(TAG, "It's a panic case. Both api value and existing value are blank.")
      }
      sharedPref.edit().putString(label,value).apply()
      return value;
    } else {
      Log.d(TAG, "ID:"+label+" API Value:"+value+" Existing Value:"+ret!!+" Constancy:"+(ret.equals(value)))
      return ret;
    }
  }
}