// SVILUPPO-20261114-02: regola pura di onReceivedSslError (legacy) e soglia della WebView.
package net.spotlive.player.piattaforma

import net.spotlive.player.piattaforma.WebViewLegacy.SSL_EXPIRED
import net.spotlive.player.piattaforma.WebViewLegacy.SSL_IDMISMATCH
import net.spotlive.player.piattaforma.WebViewLegacy.SSL_UNTRUSTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewLegacyTest {
    private val host = "209-227-230-76.sslip.io"
    private val foglia = "ab".repeat(32)

    private fun p(
        attiva: Boolean = true,
        primario: Int = SSL_UNTRUSTED,
        errori: Set<Int> = setOf(SSL_UNTRUSTED),
        hostRichiesta: String? = host,
        hostPlayer: String? = host,
        fogliaWebView: String? = foglia,
        fogliaVerificata: String? = foglia,
    ) = WebViewLegacy.puoProcedere(attiva, primario, errori, hostRichiesta, hostPlayer, fogliaWebView, fogliaVerificata)

    @Test fun procedeSoloNelCasoPrevisto() {
        assertTrue(p())
        assertTrue(p(hostRichiesta = host.uppercase()))
    }

    @Test fun rifiutaTuttoIlResto() {
        assertFalse(p(attiva = false)) // standard, o legacy su API ≥ 24
        assertFalse(p(primario = SSL_EXPIRED, errori = setOf(SSL_EXPIRED)))
        assertFalse(p(errori = setOf(SSL_UNTRUSTED, SSL_IDMISMATCH)))
        assertFalse(p(errori = setOf(SSL_UNTRUSTED, SSL_EXPIRED)))
        assertFalse(p(errori = setOf(SSL_UNTRUSTED, WebViewLegacy.SSL_DATE_INVALID)))
        assertFalse(p(errori = setOf(SSL_UNTRUSTED, WebViewLegacy.SSL_NOTYETVALID)))
        assertFalse(p(errori = emptySet()))
        assertFalse(p(hostRichiesta = "altro.example"))
        assertFalse(p(hostRichiesta = null))
        assertFalse(p(hostPlayer = null))
        assertFalse(p(fogliaWebView = null)) // chiave x509-certificate assente
        assertFalse(p(fogliaVerificata = null)) // client nativo non ancora riuscito
        assertFalse(p(fogliaWebView = "cd".repeat(32))) // foglia diversa (rinnovo o intercettazione)
    }

    @Test fun costantiUgualiAdAndroid() {
        assertEquals(android.net.http.SslError.SSL_NOTYETVALID, WebViewLegacy.SSL_NOTYETVALID)
        assertEquals(android.net.http.SslError.SSL_EXPIRED, WebViewLegacy.SSL_EXPIRED)
        assertEquals(android.net.http.SslError.SSL_IDMISMATCH, WebViewLegacy.SSL_IDMISMATCH)
        assertEquals(android.net.http.SslError.SSL_UNTRUSTED, WebViewLegacy.SSL_UNTRUSTED)
        assertEquals(android.net.http.SslError.SSL_DATE_INVALID, WebViewLegacy.SSL_DATE_INVALID)
        assertEquals(android.net.http.SslError.SSL_INVALID, WebViewLegacy.SSL_INVALID)
    }

    @Test fun impronta() {
        assertNull(WebViewLegacy.impronta(null))
        assertNull(WebViewLegacy.impronta(ByteArray(0)))
        // SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WebViewLegacy.impronta("abc".toByteArray()))
    }

    @Test fun sogliaWebView() {
        assertEquals(39, WebViewLegacy.chromePrincipale("39.0.0.0"))
        assertEquals(false, WebViewLegacy.sufficiente("39.0.0.0", 61))
        assertEquals(true, WebViewLegacy.sufficiente("61.0.3163.98", 61))
        assertEquals(true, WebViewLegacy.sufficiente("106.0.5249.126", 61))
        assertNull(WebViewLegacy.sufficiente("?", 61))
        assertNull(WebViewLegacy.sufficiente("39.0.0.0", 0)) // standard: soglia spenta
    }
}
