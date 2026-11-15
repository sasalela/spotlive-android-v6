// SVILUPPO-20261114-02: regole pure per la WebView della variante legacy (Android 5.1, Taurus T3).
package net.spotlive.player.piattaforma

import java.security.MessageDigest

/**
 * Regole pure (provate in WebViewLegacyTest, senza dispositivo):
 * - quando la WebView sotto API 24 può accettare il certificato del pannello che le sue CA non riconoscono;
 * - se la versione di Chrome della WebView basta al player web.
 */
object WebViewLegacy {
    // Valori di android.net.http.SslError (API 1-14), ripetuti per tenere la regola senza Android.
    const val SSL_NOTYETVALID = 0
    const val SSL_EXPIRED = 1
    const val SSL_IDMISMATCH = 2
    const val SSL_UNTRUSTED = 3
    const val SSL_DATE_INVALID = 4
    const val SSL_INVALID = 5

    /**
     * `proceed()` solo se TUTTE valgono:
     * - variante legacy su API < 24 (`attiva`);
     * - errore principale SSL_UNTRUSTED e nessun altro errore (scadenza, data, nome host, invalido);
     * - host della richiesta uguale all'host di URL_PLAYER;
     * - foglia vista dalla WebView (SHA-256) uguale alla foglia già verificata dal client nativo
     *   (catena PKIX verso le CA di sistema + ISRG e nome host controllati da OkHttp).
     * In ogni altro caso il chiamante esegue `cancel()`.
     */
    fun puoProcedere(
        attiva: Boolean,
        primario: Int,
        errori: Set<Int>,
        hostRichiesta: String?,
        hostPlayer: String?,
        fogliaWebView: String?,
        fogliaVerificata: String?,
    ): Boolean {
        if (!attiva) return false
        if (primario != SSL_UNTRUSTED) return false
        if (errori != setOf(SSL_UNTRUSTED)) return false
        if (hostRichiesta.isNullOrEmpty() || hostPlayer.isNullOrEmpty()) return false
        if (!hostRichiesta.equals(hostPlayer, ignoreCase = true)) return false
        if (fogliaWebView.isNullOrEmpty() || fogliaVerificata.isNullOrEmpty()) return false
        return MessageDigest.isEqual(fogliaWebView.toByteArray(), fogliaVerificata.toByteArray())
    }

    /** SHA-256 esadecimale di un certificato DER; null se assente. */
    fun impronta(der: ByteArray?): String? {
        if (der == null || der.isEmpty()) return null
        return MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
    }

    /** Versione principale di Chrome ("39.0.0.0" → 39); null se illeggibile. */
    fun chromePrincipale(chrome: String?): Int? = chrome?.substringBefore('.')?.toIntOrNull()

    /** true/false se la WebView basta al player web; null se la versione non si legge o la soglia è spenta (0). */
    fun sufficiente(chrome: String?, minimo: Int): Boolean? {
        if (minimo <= 0) return null
        val v = chromePrincipale(chrome) ?: return null
        return v >= minimo
    }
}
