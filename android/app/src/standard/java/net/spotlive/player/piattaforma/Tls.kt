// SVILUPPO-20261114-02: fiducia TLS della variante standard = quella della 1.0.15 (nessun cambio).
package net.spotlive.player.piattaforma

import android.content.Context
import okhttp3.OkHttpClient
import javax.net.ssl.SSLSocketFactory

/** Standard (Android 7+): fiducia della piattaforma e network_security_config, come nella 1.0.15. */
object Tls {
    fun inizializza(@Suppress("UNUSED_PARAMETER") c: Context) = Unit

    fun applica(b: OkHttpClient.Builder): OkHttpClient.Builder = b

    fun socketFactory(): SSLSocketFactory? = null

    /** Usata solo dalla legacy (WebView sotto API 24). */
    fun fogliaVerificata(@Suppress("UNUSED_PARAMETER") host: String?): String? = null

    /** Usata solo dalla legacy (WebView sotto API 24). */
    fun verificaFoglia(@Suppress("UNUSED_PARAMETER") host: String): String? = null
}
