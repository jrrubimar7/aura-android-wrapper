package io.github.jrrubimar7.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQ_FILE = 201
    private val REQ_CAMERA = 202

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true

        webView.webChromeClient = object : WebChromeClient() {}
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.loadUrl("https://jrrubimar7.github.io/aura-infinito/index.html")
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun openFile() {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(Intent.createChooser(intent, "Seleccionar archivo"), REQ_FILE)
        }

        @JavascriptInterface
        fun openCamera() {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, REQ_CAMERA)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQ_FILE -> {
                val uri: Uri? = data?.data
                sendToJS(uri?.toString() ?: "")
            }
            REQ_CAMERA -> {
                sendToJS("camera://image")
            }
        }
    }

    private fun sendToJS(value: String) {
        val safe = value.replace("'", "\'")
        webView.evaluateJavascript("window.onNativeClip && window.onNativeClip('$safe')", null)
    }
}
