package net.spotlive.player.sistema

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ZonaLedTest {
    private val c: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun predefinitaSchermoIntero() {
        assertEquals("auto", ZonaLed.modalita(c))
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
    }

    @Test
    fun manualeDaTesto() {
        assertEquals("manuale 256×384", ZonaLed.daTesto(c, "256x384"))
        assertEquals(ZonaLed.Zona(256, 384), ZonaLed.effettiva(c, 1920, 1080))
        assertEquals("manuale 128×64", ZonaLed.daTesto(c, "0,0,128,64"))
        assertNull(ZonaLed.daTesto(c, "10,0,128,64"))
        assertNull(ZonaLed.daTesto(c, "5x5"))
        assertNull(ZonaLed.daTesto(c, "abc"))
        // zona più grande dell'uscita = schermo intero
        ZonaLed.daTesto(c, "1920x1080")
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
        assertEquals("schermo intero", ZonaLed.daTesto(c, "intero"))
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
    }

    @Test
    fun automaticaSoloLedPiuPiccolo() {
        ZonaLed.daTesto(c, "auto")
        assertFalse(ZonaLed.daPannello(c, "tablet", 256, 384, 1920, 1080))
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
        assertFalse(ZonaLed.daPannello(c, "led", 1920, 1080, 1920, 1080))
        assertTrue(ZonaLed.daPannello(c, "led", 256, 384, 1920, 1080))
        assertEquals(ZonaLed.Zona(256, 384), ZonaLed.effettiva(c, 1920, 1080))
        // il manuale vince sull'automatica, "intero" la spegne
        ZonaLed.daTesto(c, "intero")
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
        ZonaLed.daTesto(c, "auto")
        assertTrue(ZonaLed.daPannello(c, "led", 4000, 384, 1920, 1080))
        assertNull(ZonaLed.effettiva(c, 1920, 1080))
    }
}
