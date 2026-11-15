package net.spotlive.player.cache

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.InputStream

class IntercettoreMedia(private val cache: CacheNativa) {
    fun intercetta(richiesta: WebResourceRequest): WebResourceResponse? {
        val url = richiesta.url
        if (url.scheme != "spotlive-media") return null
        val sha = url.host ?: return null
        val stream: InputStream = cache.apriStream(sha) ?: return null
        val mime = mimeDaEstensione(sha)
        return WebResourceResponse(mime, null, 200, "OK", intestazioniRange(), stream)
    }

    private fun mimeDaEstensione(sha: String): String {
        // Il tipo reale non è nel nome file (solo hash); il player conosce il tipo dal manifest.
        return "application/octet-stream"
    }

    private fun intestazioniRange(): Map<String, String> =
        mapOf("Accept-Ranges" to "bytes", "Access-Control-Allow-Origin" to "*")
}
