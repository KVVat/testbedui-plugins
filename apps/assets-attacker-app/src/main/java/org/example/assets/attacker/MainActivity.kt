package org.example.assets.attacker

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import org.example.assets.attacker.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

//The module simply record Unique Id to the configuration file
class MainActivity : AppCompatActivity() {
  val TAG:String = "FDP_ACC_1_TEST";

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    //Prepare Data
    val res:Array<Boolean> = arrayOf(true,true,true,true);
    //Access Check

    res[0] = "Success".equals(getPrefValueOrWrite("SampleValue",""));
    Log.d("Info/"+TAG,"Preference:" +res[0])

    if(!File(applicationContext.getFilesDir(), "testfile.txt").exists()){
      res[1] = false
    }
    Log.d("Info/"+TAG,"File:" +res[1])
    CoroutineScope(Dispatchers.IO).launch {
      val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
          )
        } else {
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

      val contentResolver = application.contentResolver
      val cursor = contentResolver.query(collection,
                                         arrayOf(MediaStore.Video.Media._ID,MediaStore.Images.Media.DISPLAY_NAME),
                                         "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(TAG+".jpg"),
                                         null);
      if (cursor != null && cursor.moveToFirst()) {
        Log.d("Info/"+TAG, "Media: Found")
        cursor.close()
      } else {
        res[2] = false;
        Log.d("Info/"+TAG,"Media: Not Found");
      }
    }
    CoroutineScope(Dispatchers.IO).launch {
      //Check
      val db = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java, "database-name"
      ).build()
      //
      if(db.userDao().loadAllByIds(intArrayOf(1)).size == 0){
        res[3] = false;
      }
    }
    Thread.sleep(2000);
    Log.d(TAG,"Test Result:${res[0]}/${res[1]}/${res[2]}/${res[3]}");
  }

  fun getPrefValueOrWrite(label:String,value:String):String{
    val sharedPref = getSharedPreferences(TAG, Context.MODE_PRIVATE)
    val ret = sharedPref.getString(label,"")
    return if(ret==""){
      if(!ret.equals(value)){
        Log.d("Pref", "New API Value=>"+value+" assigned.")
      } else {
        Log.d("Pref", "It's a panic case. Both api value and existing value are blank.")
      }
      sharedPref.edit().putString(label,value).apply()
      value;
    } else {
      Log.d("Pref", "ID:"+label+" API Value:"+value+" Existing Value:"+ret!!+" Constancy:"+(ret.equals(value)))
      ret;
    }
  }

}