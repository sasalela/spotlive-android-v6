// SVILUPPO-20261114-02: fiducia TLS della variante legacy (Taurus T3, Android 5.1).
package net.spotlive.player.piattaforma

import android.content.Context
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Legacy: Android 5.1 non conosce ISRG Root X1 (Let's Encrypt). Ancore = CA di sistema (AndroidCAStore, solo
 * "system:") + ISRG Root X1/X2 da res/raw. La verifica resta quella PKIX standard (TrustManagerFactory), il nome
 * host lo controlla sempre OkHostnameVerifier (non toccato). Solo TLS moderno: niente CLEARTEXT, perché su API 22
 * network_security_config viene ignorato.
 */
object Tls {
    private class Materiale(val tm: X509TrustManager, val ctx: SSLContext)

    @Volatile private var app: Context? = null
    @Volatile private var materiale: Materiale? = null
    private val foglie = ConcurrentHashMap<String, String>()

    fun inizializza(c: Context) {
        app = c.applicationContext
    }

    fun applica(b: OkHttpClient.Builder): OkHttpClient.Builder {
        val m = materiale()
        if (m != null) b.sslSocketFactory(m.ctx.socketFactory, m.tm)
        return b.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
    }

    fun socketFactory(): SSLSocketFactory? = materiale()?.ctx?.socketFactory

    /** SHA-256 della foglia del pannello già verificata dal client nativo (null = non ancora). */
    fun fogliaVerificata(host: String?): String? = host?.let { foglie[it.lowercase()] }

    /**
     * Apre una connessione nativa al pannello (catena PKIX verso le ancore + nome host) e ricorda lo SHA-256
     * della foglia. Rete: mai sul thread principale. null = connessione o verifica fallita.
     */
    fun verificaFoglia(host: String): String? {
        val client = applica(
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS),
        ).build()
        val richiesta = Request.Builder().url("https://$host/player/").header("Cache-Control", "no-cache").build()
        return runCatching {
            client.newCall(richiesta).execute().use { r ->
                val foglia = r.handshake?.peerCertificates?.firstOrNull() as? X509Certificate ?: return@use null
                impronta(foglia).also { foglie[host.lowercase()] = it }
            }
        }.getOrNull()
    }

    fun impronta(c: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString("") { "%02x".format(it) }

    @Synchronized
    private fun materiale(): Materiale? {
        materiale?.let { return it }
        val contesto = app
        return runCatching {
            val isrg = if (contesto == null) emptyList() else ancoreIsrg(contesto)
            val tm = trustManager(ancoreSistema() + isrg)
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            Materiale(tm, ctx)
        }.getOrNull()?.also {
            // Senza contesto (mai in app: inizializza() è la prima riga di PlayerApplicazione) non si memorizza.
            if (contesto != null) materiale = it
        }
    }

    private fun ancoreIsrg(c: Context): List<X509Certificate> =
        listOf(net.spotlive.player.R.raw.isrg_root_x1, net.spotlive.player.R.raw.isrg_root_x2).flatMap { id ->
            c.resources.openRawResource(id).use { leggiPem(it) }
        }

    /** CA di sistema di Android (senza quelle aggiunte dall'utente); fuori da Android quelle predefinite della JVM. */
    fun ancoreSistema(): List<X509Certificate> {
        val android = runCatching {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            ks.aliases().toList().filter { it.startsWith("system:") }.mapNotNull { ks.getCertificate(it) as? X509Certificate }
        }.getOrNull()
        if (!android.isNullOrEmpty()) return android
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first().acceptedIssuers.toList()
    }

    fun leggiPem(input: InputStream): List<X509Certificate> =
        CertificateFactory.getInstance("X.509").generateCertificates(input).filterIsInstance<X509Certificate>()

    /** TrustManager PKIX standard sulle ancore date (nessuna logica di verifica scritta da noi). */
    fun trustManager(ancore: Collection<X509Certificate>): X509TrustManager {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ancore.forEachIndexed { i, c -> ks.setCertificateEntry("ancora$i", c) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
