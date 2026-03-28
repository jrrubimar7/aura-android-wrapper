package io.github.jrrubimar7.aura

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(
                    """
                    (function(){
                      try{
                        window.__ANDROID_BRIDGE_READY__ = (typeof Android !== 'undefined' && Android && typeof Android.startSTT === 'function');
                        window.__ANDROID_TTS_READY__ = ${'$'}ttsReady;
                        console.log('ANDROID_BRIDGE_READY=' + window.__ANDROID_BRIDGE_READY__);
                        console.log('ANDROID_TTS_READY=' + window.__ANDROID_TTS_READY__);
                      }catch(e){}
                    })();
                    """.trimIndent().replace("${'$'}ttsReady", ttsReady.toString()),
                    null
                )
            }
        }

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
            if (ttsReady) {
                engine.setSpeechRate(0.95f)
                engine.setPitch(1.0f)
            }
        } else {
            ttsReady = false
        }
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

        @JavascriptInterface
        fun speak(text: String?) {
            val value = text?.trim().orEmpty()
            if (value.isBlank()) return
            runOnUiThread {
                val engine = tts
                if (engine == null || !ttsReady) {
                    sendErrorToJS("TTS Android no disponible")
                    return@runOnUiThread
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.speak(value, TextToSpeech.QUEUE_FLUSH, null, "AURA_TTS")
                } else {
                    @Suppress("DEPRECATION")
                    engine.speak(value, TextToSpeech.QUEUE_FLUSH, null)
                }
            }
        }

        @JavascriptInterface
        fun stopSpeak() {
            runOnUiThread {
                tts?.stop()
            }
        }

        @JavascriptInterface
        fun ping(): String {
            return "pong"
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

        try {
            startActivityForResult(intent, REQ_CODE_SPEECH)
        } catch (e: Exception) {
            sendErrorToJS("STT no disponible")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CODE_SPEECH) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = result?.firstOrNull() ?: ""
                sendTextToJS(text)
            } else {
                sendErrorToJS("Cancelado o sin resultado")
            }
        }
    }

    private fun sendTextToJS(text: String) {
        runOnUiThread {
            val safeText = text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            webView.evaluateJavascript(
                "window.onNativeSTT && window.onNativeSTT('$safeText')",
                null
            )
        }
    }

    private fun sendErrorToJS(msg: String) {
        runOnUiThread {
            val safeMsg = msg
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            webView.evaluateJavascript(
                "window.onNativeSTTError && window.onNativeSTTError('$safeMsg')",
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

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
