package io.github.jrrubimar7.aura

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val REQ_CODE_SPEECH = 100
    private val REQ_PERMISSION_AUDIO = 200
    private val REQ_FILE = 201
    private val REQ_CAMERA = 202

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        tts = TextToSpeech(this, this)

        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.databaseEnabled = true
        ws.javaScriptCanOpenWindowsAutomatically = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {}

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.loadUrl("https://jrrubimar7.github.io/aura-infinito/index.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val result = engine.setLanguage(Locale("es", "ES"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun startSTT() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
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

        @JavascriptInterface
        fun speak(text: String?) {
            val value = text?.trim().orEmpty()
            if (value.isBlank()) return
            runOnUiThread {
                tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "AURA_TTS")
            }
        }

        @JavascriptInterface
        fun openFile() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                startActivityForResult(intent, REQ_FILE)
            }
        }

        @JavascriptInterface
        fun openCamera() {
            runOnUiThread {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(intent, REQ_CAMERA)
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")

        try {
            startActivityForResult(intent, REQ_CODE_SPEECH)
        } catch (e: Exception) {
            Toast.makeText(this, "STT no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CODE_SPEECH && resultCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = result?.firstOrNull() ?: ""

            // 🔥 TOAST DE DEBUG
            Toast.makeText(this, "STT: $text", Toast.LENGTH_LONG).show()

            // 🔥 ENVÍO A INPUT DIRECTO
            val safe = text.replace("'", "\\'")
            webView.evaluateJavascript(
                "document.querySelector('textarea, input').value = '$safe';",
                null
            )

            // 🔥 ENVÍO A AURA
            webView.evaluateJavascript(
                "window.onNativeSTT && window.onNativeSTT('$safe')",
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
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Permiso micrófono denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
