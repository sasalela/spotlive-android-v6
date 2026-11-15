package net.spotlive.player.luminosita

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RegolaLuminositaTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun regola(s: String) = CalcoloLuminosita.daManifest(Json.parseToJsonElement(s).jsonObject)!!

    // Riferimento: SunCalc 1.9.0 (stessi casi in player-desktop). Scarto ammesso 2 minuti.
    private val casi = listOf(
        arrayOf("Bari", 41.1171, 16.8719, 2026, 6, 21, "2026-06-21T03:21:19Z", "2026-06-21T18:29:40Z"),
        arrayOf("Bari", 41.1171, 16.8719, 2026, 12, 21, "2026-12-21T06:15:29Z", "2026-12-21T15:27:59Z"),
        arrayOf("Milano", 45.4642, 9.19, 2026, 6, 21, "2026-06-21T03:35:50Z", "2026-06-21T19:16:36Z"),
        arrayOf("Milano", 45.4642, 9.19, 2026, 12, 21, "2026-12-21T07:01:18Z", "2026-12-21T15:43:38Z"),
        arrayOf("Palermo", 38.1157, 13.3615, 2026, 3, 29, "2026-03-29T04:57:57Z", "2026-03-29T17:27:12Z"),
        arrayOf("Palermo", 38.1157, 13.3615, 2026, 10, 25, "2026-10-25T05:25:56Z", "2026-10-25T16:17:43Z"),
    )

    @Test
    fun albaETramontoEntroDueMinuti() {
        for (c in casi) {
            val a = CalcoloLuminosita.sole(c[3] as Int, c[4] as Int, c[5] as Int, c[1] as Double, c[2] as Double, true)!!
            val t = CalcoloLuminosita.sole(c[3] as Int, c[4] as Int, c[5] as Int, c[1] as Double, c[2] as Double, false)!!
            assertTrue("${c[0]} ${c[3]}-${c[4]}-${c[5]} alba scarto ${(a - ms(c[6] as String)) / 1000}s", kotlin.math.abs(a - ms(c[6] as String)) <= 120_000)
            assertTrue("${c[0]} tramonto scarto ${(t - ms(c[7] as String)) / 1000}s", kotlin.math.abs(t - ms(c[7] as String)) <= 120_000)
        }
    }

    @Test
    fun campoAssenteOSpentoNonDaRegola() {
        assertNull(CalcoloLuminosita.daManifest(null))
        assertNull(CalcoloLuminosita.daManifest(Json.parseToJsonElement("""{"attiva":false,"modo":"sole"}""").jsonObject))
    }

    @Test
    fun soleGiornoENotteConGradini() {
        val r = regola("""{"modo":"sole","giornoPct":90,"nottePct":20,"spostaTramontoMin":0,"spostaAlbaMin":0,"notteDalle":"19:00","giornoDalle":"07:00","lat":41.1171,"lon":16.8719}""")
        // Bari 21/12/2026: tramonto ~15:28 UTC (16:28 ora di Roma).
        assertEquals(90, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T12:00:00Z")).pct)
        val t = CalcoloLuminosita.sole(2026, 12, 21, 41.1171, 16.8719, false)!!
        val valori = listOf(0L, 5, 10, 15, 20, 60).map { CalcoloLuminosita.attesa(r, "Europe/Rome", t + it * 60_000 + 1000).pct }
        // 4 gradini: 90 → 72.5 → 55 → 37.5 → 20, valore finale dal minuto 15.
        assertEquals(listOf(73, 55, 38, 20, 20, 20), valori)
        assertEquals("notte", CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T23:00:00Z")).fase)
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-22T03:00:00Z")).pct)
    }

    @Test
    fun spostamentoTramonto() {
        val r = regola("""{"modo":"sole","giornoPct":90,"nottePct":20,"spostaTramontoMin":30,"lat":41.1171,"lon":16.8719}""")
        val t = CalcoloLuminosita.sole(2026, 12, 21, 41.1171, 16.8719, false)!!
        assertEquals(90, CalcoloLuminosita.attesa(r, "Europe/Rome", t + 20 * 60_000).pct)
        assertEquals(73, CalcoloLuminosita.attesa(r, "Europe/Rome", t + 31 * 60_000).pct)
    }

    @Test
    fun senzaPosizioneUsaOrariFissi() {
        val r = regola("""{"modo":"sole","giornoPct":90,"nottePct":20,"notteDalle":"19:00","giornoDalle":"07:00","lat":null,"lon":null}""")
        // 18:59 ora di Roma (inverno, UTC+1) = 17:59Z → giorno; 19:30 → notte piena.
        assertEquals(90, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T17:59:00Z")).pct)
        assertEquals(73, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T18:00:30Z")).pct)
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T18:30:00Z")).pct)
        // Mezzanotte e prima delle 7: notte.
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T23:30:00Z")).pct)
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-22T05:59:00Z")).pct)
        // Ora legale (estate, UTC+2): 19:00 di Roma = 17:00Z.
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-07-01T17:30:00Z")).pct)
    }

    @Test
    fun orariACavalloDellaMezzanotte() {
        // Giorno dalle 22:00, notte dalle 06:00 (insegna notturna): a mezzanotte è "giorno".
        val r = regola("""{"modo":"orari","giornoPct":80,"nottePct":30,"notteDalle":"06:00","giornoDalle":"22:00"}""")
        assertEquals("giorno", CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-21T23:30:00Z")).fase)
        assertEquals("notte", CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-12-22T12:00:00Z")).fase)
    }

    @Test
    fun cambioOraLegale() {
        // 25/10/2026 a Roma si torna all'ora solare: 07:00 locale = 06:00Z.
        val r = regola("""{"modo":"orari","giornoPct":90,"nottePct":20,"notteDalle":"19:00","giornoDalle":"07:00"}""")
        assertEquals(20, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-10-25T05:59:00Z")).pct)
        assertEquals(38, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-10-25T06:00:30Z")).pct)
    }

    @Test
    fun polareRipiegaSugliOrari() {
        val r = regola("""{"modo":"sole","giornoPct":90,"nottePct":20,"lat":80.0,"lon":15.0}""")
        assertEquals(90, CalcoloLuminosita.attesa(r, "Europe/Rome", ms("2026-06-21T12:00:00Z")).pct)
    }

    @Test
    fun valoriFuoriLimiteRiportatiNeiLimiti() {
        val r = regola("""{"modo":"orari","giornoPct":150,"nottePct":0}""")
        assertEquals(100, r.giornoPct)
        assertEquals(5, r.nottePct)
    }
}
