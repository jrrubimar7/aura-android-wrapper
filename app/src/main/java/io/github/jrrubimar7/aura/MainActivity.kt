package io.github.jrrubimar7.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQ_CODE_SPEECH = 100
    private val REQ_PERMISSION_AUDIO = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.loadUrl("https://jrrubimar7.github.io/aura-infinito/index.html")
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun startSTT() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQ_PERMISSION_AUDIO
                    )
                } else {
                    startSpeechRecognition()
                }
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")

        try {
            startActivityForResult(intent, REQ_CODE_SPEECH)
        } catch (e: Exception) {
            sendErrorToJS("STT no disponible")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CODE_SPEECH) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = result?.get(0) ?: ""

                sendTextToJS(text)

            } else {
                sendErrorToJS("Cancelado o sin resultado")
            }
        }
    }

    private fun sendTextToJS(text: String) {
        runOnUiThread {
            val safeText = text.replace("'", "\\'")
            webView.evaluateJavascript(
                "window.onNativeSTT('$safeText')",
                null
            )
        }
    }

    private fun sendErrorToJS(msg: String) {
        runOnUiThread {
            val safeMsg = msg.replace("'", "\\'")
            webView.evaluateJavascript(
                "window.onNativeSTTError('$safeMsg')",
                null
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_PERMISSION_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeechRecognition()
            } else {
                sendErrorToJS("Permiso de micrófono denegado")
            }
        }
    }
}
