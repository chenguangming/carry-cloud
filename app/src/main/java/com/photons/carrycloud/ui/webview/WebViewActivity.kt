package com.photons.carrycloud.ui.webview

import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPress)

        initViews()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            else -> return false
        }
        return super.onOptionsItemSelected(item)
    }

    private val onBackPress = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.webview.canGoBack()) {
                binding.webview.goBack()
            } else {
                finish()
            }
        }
    }

    private fun initViews() {
        setSupportActionBar(binding.topbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val url = intent.getStringExtra("html")
        val title = intent.getStringExtra("title")
        if (!TextUtils.isEmpty(title)) {
            binding.topbar.title = title
        }

        if (TextUtils.isEmpty(url)) {
            finish()
            return
        }

        binding.webview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (isFinishing){
                        return
                    }
                    val webTitle = view.title
                    if (!url.contains(webTitle!!)) {
                        binding.topbar.title = webTitle
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (isFinishing) {
                        return
                    }

                    if (newProgress == 100) {
                        binding.progressbar.visibility = View.INVISIBLE //加载完网页进度条消失
                    } else {
                        binding.progressbar.visibility = View.VISIBLE //开始加载网页时显示进度条
                        binding.progressbar.progress = newProgress //设置进度值
                    }
                }
            }

            loadUrl(url!!)
        }

        binding.swipeRefreshLayout.apply {
            setColorSchemeColors(getColor(R.color.colorPrimary))
            setProgressBackgroundColorSchemeResource(R.color.s_app_color_gray)
            setOnRefreshListener {
                binding.webview.reload()
                isRefreshing = false
            }
        }
    }
}