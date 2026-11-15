package net.spotlive.player.ponte

import android.webkit.JavascriptInterface

class PonteJavascript(private val gestore: PonteGestore) {
    @JavascriptInterface
    fun richiedi(json: String): String = gestore.gestisci(json)
}
