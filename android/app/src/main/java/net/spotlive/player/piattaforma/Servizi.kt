// SVILUPPO-20261114-02: servizi di sistema anche sotto API 23 (variante legacy, Taurus T3 su Android 5.1).
package net.spotlive.player.piattaforma

import android.content.Context
import android.os.Build

/**
 * `getSystemService(Class)` esiste solo da API 23. Da 23 in su si chiama esattamente come nella 1.0.15;
 * sotto si usa il nome del servizio (`Context.*_SERVICE`).
 */
inline fun <reified T> servizio(c: Context, nome: String): T =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        c.getSystemService(T::class.java)
    } else {
        c.getSystemService(nome) as T
    }

/** Stato della variante (standard/legacy) letto dalla diagnosi e da `PonteGestore.stato()`. */
object Variante {
    val legacy: Boolean get() = net.spotlive.player.BuildConfig.VARIANTE == "legacy"

    /** Solo legacy: false se l'archivio è rimasto in chiaro (Keystore non disponibile); null nella standard. */
    @Volatile var archivioCifrato: Boolean? = null

    /** Solo legacy: pacchetto, versione e Chrome della WebView, e se basta al player web. */
    @Volatile var webView: Map<String, Any?>? = null
}
