package com.example.appupdate

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.appupdate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val TAG = "AppUpdateTest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val info = """
            Package: ${BuildConfig.APPLICATION_ID}
            Version: ${BuildConfig.VERSION_NAME}
            Code: ${BuildConfig.VERSION_CODE}
            Type: ${BuildConfig.BUILD_TYPE}
        """.trimIndent()

        binding.mainText.text = info
        Log.i(TAG, "Application Started: \n$info")
    }
}