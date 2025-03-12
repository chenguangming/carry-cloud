package com.photons.carrycloud.ui.splash

import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.photons.carrycloud.MainActivity
import com.photons.carrycloud.databinding.ActivitySplashBinding
import com.photons.carrycloud.utils.StatusBarUtil

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        StatusBarUtil.setTranslucentStatus(this)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1000)
    }
}