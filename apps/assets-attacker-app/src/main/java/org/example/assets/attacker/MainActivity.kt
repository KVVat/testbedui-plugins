package org.example.assets.attacker

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    val TAG: String = "FDP_ACC_1_TEST"
    val TARGET_PACKAGE = "org.example.assets.target" // ★標的のパッケージ名

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // デフォルトは全てアクセス成功(true)と仮定し、失敗したら false に落とす
        val res: Array<Boolean> = arrayOf(true, true, true, true)

        // 1. Preference Attack (TargetのContextを奪取して覗き見を試みる)
        try {
            // 他アプリのContextを強制的に生成
            val targetContext = createPackageContext(TARGET_PACKAGE, 0)
            val sharedPref = targetContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            val prefValue = sharedPref.getString("SampleValue", "")
            res[0] = "Success" == prefValue
            Log.d("Info/$TAG", "Preference Attack: ${res[0]} (Value: $prefValue)")
        } catch (e: Exception) {
            Log.e("Info/$TAG", "Preference Attack Failed (Sandbox blocked): ${e.message}")
            res[0] = false // SecurityException等で弾かれたら安全証明
        }

        // 2. Internal File Attack (Linuxの絶対パスで直接ファイルを読みに行く)
        try {
            val targetFile = File("/data/data/$TARGET_PACKAGE/files", "testfile.txt")
            if (targetFile.exists() && targetFile.canRead()) {
                res[1] = true
                Log.d("Info/$TAG", "File Attack: Success (VULNERABILITY!)")
            } else {
                res[1] = false
                Log.d("Info/$TAG", "File Attack: Failed (Access Denied by Linux Permissions)")
            }
        } catch (e: Exception) {
            Log.e("Info/$TAG", "File Attack Failed: ${e.message}")
            res[1] = false
        }

        // 3. MediaStore Attack (Public Storageなのでこれは読めてしまうのが正解)
        CoroutineScope(Dispatchers.IO).launch {
            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

            val cursor = application.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf("$TAG.jpg"),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                Log.d("Info/$TAG", "Media Attack: Found Target's Image")
                res[2] = true
                cursor.close()
            } else {
                res[2] = false
                Log.d("Info/$TAG", "Media Attack: Not Found")
            }
        }

        // 4. Room DB Attack (SQLite:Access directly to DB file)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbFile = File("/data/data/$TARGET_PACKAGE/databases", "database-name")
                if (dbFile.exists() && dbFile.canRead()) {
                    res[3] = true
                    Log.d("Info/$TAG", "DB Attack: Success (VULNERABILITY!)")
                } else {
                    res[3] = false
                    Log.d("Info/$TAG", "DB Attack: Failed (Access Denied by Linux Permissions)")
                }
            } catch (e: Exception) {
                Log.e("Info/$TAG", "DB Attack Failed: ${e.message}")
                res[3] = false
            }
        }

        // 非同期処理を待つ
        Thread.sleep(2000)

        // 最終結果出力 (Targetと同じフォーマット)
        Log.i("FDP_ACC_1_TEST:RESULT", "${res[0]}/${res[1]}/${res[2]}/${res[3]}")
    }
}