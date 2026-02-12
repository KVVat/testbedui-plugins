package com.example.appupdate

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.appupdate.databinding.ActivityMainBinding
import com.example.appupdate.BuildConfig

//The application used by FDP_ACF_EXT.1 Test Case
//It holds
class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater);
    setContentView(binding.root)
  }

  override fun onStart() {
    super.onStart()
    val signature: String = BuildConfig.APPLICATION_ID + "\n" +
      "Version:" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE + "\n" + BuildConfig.BUILD_TYPE
    binding.mainText.text = signature;
  }
}