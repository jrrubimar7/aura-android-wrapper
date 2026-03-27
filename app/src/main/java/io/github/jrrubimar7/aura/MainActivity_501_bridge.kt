package io.github.jrrubimar7.aura

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.jrrubimar7.aura.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val startUrl = "https://jrrubimar7.github.io/aura-infinito/"
    private val MIC_PERMISSION_REQUEST = 1001
    private val STT_REQUEST_CODE = 2001
    private var returnedFromAuth = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestMicPermission()
        configureWebView(binding.webView)
        binding.webView.addJavascriptInterface(AndroidBridge(this), "Android")

        if (savedInstanceState == null) {
            binding.webView.loadUrl(startUrl)
        } else {
            binding.webView.restoreState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        CookieManager.getInstance().flush()
        if (returnedFromAuth) {
            returnedFromAuth = false
            binding.webView.post {
                binding.webView.loadUrl(startUrl)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST
            )
        }
    }

    fun startNativeSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con AURA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching {
            startActivityForResult(intent, STT_REQUEST_CODE)
        }.onFailure { error ->
            binding.webView.post {
                binding.webView.evaluateJavascript(
                    "window.onNativeSTTError && window.onNativeSTTError(${JSONObject.quote(error.message ?: "No se pudo iniciar STT")});",
                    null
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = settings.userAgentString + " AURA-Android/1.0"
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.contains("accounts.google.com", ignoreCase = true) ||
                    url.contains("oauth2.googleapis.com", ignoreCase = true) ||
                    url.contains("puter.com/login", ignoreCase = true) ||
                    url.contains("puter.com/auth", ignoreCase = true) ||
                    url.contains("api.puter.com/auth", ignoreCase = true) ||
                    url.contains("accounts.puter.com", ignoreCase = true)
                ) {
                    returnedFromAuth = true
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler,
                error: SslError?
            ) {
                handler.cancel()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == STT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = result?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    binding.webView.post {
                        binding.webView.evaluateJavascript(
                            "window.onNativeSTT && window.onNativeSTT(${JSONObject.quote(text)});",
                            null
                        )
                    }
                }
            } else {
                binding.webView.post {
                    binding.webView.evaluateJavascript(
                        "window.onNativeSTTError && window.onNativeSTTError('STT cancelado o sin resultado');",
                        null
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }
}
