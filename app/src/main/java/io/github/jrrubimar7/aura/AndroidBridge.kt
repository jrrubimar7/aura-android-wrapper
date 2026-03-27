package io.github.jrrubimar7.aura

import android.webkit.JavascriptInterface

class AndroidBridge(
    private val activity: MainActivity
) {
    @JavascriptInterface
    fun startSTT() {
        activity.runOnUiThread {
            activity.startNativeSpeechToText()
        }
    }
}
