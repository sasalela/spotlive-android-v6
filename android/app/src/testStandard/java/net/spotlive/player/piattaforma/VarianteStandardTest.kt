// SVILUPPO-20261114-02: la variante standard resta la 1.0.15 (versione, fiducia TLS della piattaforma).
package net.spotlive.player.piattaforma

import net.spotlive.player.BuildConfig
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VarianteStandardTest {
    @Test fun versioneInvariata() {
        assertEquals("standard", BuildConfig.VARIANTE)
        assertEquals("1.0.15", BuildConfig.VERSIONE_PLAYER)
        assertEquals("1.0.15", BuildConfig.VERSION_NAME)
        assertEquals(16, BuildConfig.VERSION_CODE)
        assertEquals(0, BuildConfig.WEBVIEW_CHROME_MIN)
        assertFalse(Variante.legacy)
    }

    @Test fun tlsIdentita() {
        val b = OkHttpClient.Builder()
        assertSame(b, Tls.applica(b))
        assertNull(Tls.socketFactory())
        assertNull(Tls.fogliaVerificata("209-227-230-76.sslip.io"))
    }
}
