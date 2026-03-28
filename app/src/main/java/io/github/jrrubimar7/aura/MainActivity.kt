package io.github.jrrubimar7.aura

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.ConsoleMessage
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQ_CODE_SPEECH = 100
    private val REQ_PERMISSION_AUDIO = 200

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this)
        setContentView(webView)

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
                logToJs("PAGE_FINISHED: " + (url ?: "null"))
                webView.evaluateJavascript(
                    """
                    (function(){
                      try{
                        var ok = (typeof Android !== 'undefined' && Android && typeof Android.startSTT === 'function');
                        window.__ANDROID_BRIDGE_READY__ = ok;
                        if(typeof toast==='function') toast('Bridge Android: ' + (ok ? 'OK' : 'NO'));
                        console.log('ANDROID_BRIDGE_READY=' + ok);
                      }catch(e){
                        if(typeof toast==='function') toast('Bridge Android error');
                        console.log('ANDROID_BRIDGE_ERROR=' + e.message);
                      }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.loadUrl("https://jrrubimar7.github.io/aura-infinito/index.html")
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun startSTT() {
            runOnUiThread {
                logToJs("Android.startSTT() llamado")
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    logToJs("Permiso RECORD_AUDIO no concedido, pidiéndolo")
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQ_PERMISSION_AUDIO
                    )
                } else {
                    logToJs("Permiso RECORD_AUDIO OK, iniciando reconocimiento")
                    startSpeechRecognition()
                }
            }
        }

        @JavascriptInterface
        fun ping(): String {
            return "pong"
        }
    }

    private fun startSpeechRecognition() {
        logToJs("startSpeechRecognition()")
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
            logToJs("Intent STT lanzado")
        } catch (e: Exception) {
            logToJs("Error lanzando STT: " + (e.message ?: "desconocido"))
            sendErrorToJS("STT no disponible")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        logToJs("onActivityResult requestCode=$requestCode resultCode=$resultCode")

        if (requestCode == REQ_CODE_SPEECH) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = result?.firstOrNull() ?: ""
                logToJs("STT texto reconocido: " + if (text.isBlank()) "(vacío)" else text)
                sendTextToJS(text)
            } else {
                logToJs("STT cancelado o sin resultado")
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
            logToJs("Enviando texto a JS")
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
            logToJs("Enviando error a JS: $msg")
            webView.evaluateJavascript(
                "window.onNativeSTTError && window.onNativeSTTError('$safeMsg')",
                null
            )
        }
    }

    private fun logToJs(msg: String) {
        runOnUiThread {
            val safeMsg = msg
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            webView.evaluateJavascript(
                """
                (function(){
                  try{
                    console.log('[AURA-ANDROID] ' + '$safeMsg');
                    if(typeof toast==='function') toast('$safeMsg');
                  }catch(e){}
                })();
                """.trimIndent(),
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
                logToJs("Permiso RECORD_AUDIO concedido")
                startSpeechRecognition()
            } else {
                logToJs("Permiso RECORD_AUDIO denegado")
                sendErrorToJS("Permiso de micrófono denegado")
            }
        }
    }
}
