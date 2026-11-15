package net.spotlive.player.sistema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausaAssistenzaTest {
    private val minuto = 60_000L
    private val ora = 5_000_000L

    @Test
    fun durateAmmesseSoloTrentaESessanta() {
        assertTrue(PausaAssistenza.durataValida(30))
        assertTrue(PausaAssistenza.durataValida(60))
        for (m in listOf(10, 0, 90, 120, -30, -1)) assertFalse("$m", PausaAssistenza.durataValida(m))
    }

    @Test
    fun appenaImpostataTrentaMinuti() {
        assertEquals(30 * minuto, PausaAssistenza.restanteMs(ora + 30 * minuto, ora))
        assertEquals(60 * minuto, PausaAssistenza.restanteMs(ora + 60 * minuto, ora))
    }

    @Test
    fun scadutaValeZero() {
        assertEquals(0L, PausaAssistenza.restanteMs(ora, ora))
        assertEquals(0L, PausaAssistenza.restanteMs(ora - 1, ora))
        assertEquals(0L, PausaAssistenza.restanteMs(0, ora)) // chiave assente
    }

    @Test
    fun residuoDaDueOreInSuSiIgnora() {
        // Valore rimasto da prima di un riavvio: elapsedRealtime riparte da zero e "fino" resta lontano.
        assertEquals(0L, PausaAssistenza.restanteMs(ora + 2 * 60 * minuto, ora))
        assertEquals(0L, PausaAssistenza.restanteMs(50 * 60 * 60 * minuto, 10_000L))
        assertEquals(2 * 60 * minuto - 1, PausaAssistenza.restanteMs(ora + 2 * 60 * minuto - 1, ora))
    }

    @Test
    fun watchdogSaltaSoloConPausaAttiva() {
        assertTrue(PausaAssistenza.watchdogDeveSaltare(1))
        assertTrue(PausaAssistenza.watchdogDeveSaltare(30 * minuto))
        assertFalse(PausaAssistenza.watchdogDeveSaltare(0))
    }

    @Test
    fun fuoriAssistenzaRiapreDopoDueControlliComeOggi() {
        assertFalse(PausaAssistenza.watchdogDeveIntervenireDopoMancati(1, 0, visibile = false))
        assertTrue(PausaAssistenza.watchdogDeveIntervenireDopoMancati(2, 0, visibile = false))
        assertTrue(PausaAssistenza.watchdogDeveIntervenireDopoMancati(3, 0, visibile = false))
    }

    @Test
    fun visibileOInAssistenzaNonRiapre() {
        assertFalse(PausaAssistenza.watchdogDeveIntervenireDopoMancati(2, 0, visibile = true))
        assertFalse(PausaAssistenza.watchdogDeveIntervenireDopoMancati(2, 30 * minuto, visibile = false))
        assertFalse(PausaAssistenza.watchdogDeveIntervenireDopoMancati(5, 1, visibile = false))
    }

    @Test
    fun aScadenzaIlWatchdogRiprende() {
        val fino = ora + 30 * minuto
        val dopo = fino + 1
        val restante = PausaAssistenza.restanteMs(fino, dopo)
        assertFalse(PausaAssistenza.watchdogDeveSaltare(restante))
        assertTrue(PausaAssistenza.watchdogDeveIntervenireDopoMancati(2, restante, visibile = false))
    }
}
