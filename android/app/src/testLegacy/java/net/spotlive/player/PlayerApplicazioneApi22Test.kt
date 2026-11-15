// SVILUPPO-20261114-02: avvio dell'applicazione legacy su sdk 22 senza AndroidKeyStore: nessun crash.
package net.spotlive.player

import androidx.test.core.app.ApplicationProvider
import net.spotlive.player.piattaforma.ArchivioSicuro
import net.spotlive.player.piattaforma.Variante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class PlayerApplicazioneApi22Test {
    @Test fun avvioSenzaCrash() {
        val app = ApplicationProvider.getApplicationContext<PlayerApplicazione>()
        assertNotNull(Variante.archivioCifrato)
        app.archivioSicuro.scrivi(ArchivioSicuro.CHIAVE_TOKEN, "t")
        assertEquals(Pair(true, "t"), app.archivioSicuro.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        assertEquals("legacy", BuildConfig.VARIANTE)
        assertEquals("1.0.15-legacy", BuildConfig.VERSIONE_PLAYER)
        assertEquals("1.0.15-legacy", BuildConfig.VERSION_NAME)
        assertEquals(16, BuildConfig.VERSION_CODE)
        assertEquals(61, BuildConfig.WEBVIEW_CHROME_MIN)
    }
}
