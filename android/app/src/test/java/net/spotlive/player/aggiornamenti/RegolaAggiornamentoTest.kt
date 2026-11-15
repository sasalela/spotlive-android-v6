package net.spotlive.player.aggiornamenti

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegolaAggiornamentoTest {
    private val sha = "a".repeat(64)
    private val cert = RegolaAggiornamento.CERT_SPOTLIVE
    private fun o(s: String) = Json.parseToJsonElement(s).jsonObject
    private fun ist(code: Int, auto: Boolean = true, fonte: String = "server") =
        IstruzioneAndroid("prova", code, "x", "https://h/a.apk", sha, cert, auto, fonte)

    @Test
    fun leggeIstruzioneServerConUrlRelativo() {
        val i = RegolaAggiornamento.leggi(
            o("""{"canale":"prova","versionCode":16,"versionName":"1.0.15","url":"/aggiornamenti/android/prova/spotlive-player-1.0.15.apk","sha256":"${sha.uppercase()}","certSha256":"08:14:aa:ef:b1:64:36:da:a2:75:4a:ad:e7:17:5a:c8:0e:55:6f:26:45:87:ec:f9:b9:8c:d8:dc:e0:3a:72:88","automatico":true}"""),
            "https://209-227-230-76.sslip.io/api/v1/player/aggiornamento", "server",
        )!!
        assertEquals("https://209-227-230-76.sslip.io/aggiornamenti/android/prova/spotlive-player-1.0.15.apk", i.url)
        assertEquals(sha, i.sha256); assertEquals(cert, i.certSha256); assertEquals("prova", i.canale)
    }

    @Test
    fun leggeFileStorico() {
        val i = RegolaAggiornamento.leggi(
            o("""{"versionCode":6,"versionName":"1.0.5","url":"spotlive-player-test.apk","sha256":"$sha","automatico":false}"""),
            "https://h/scarica-x/versione-android.json", "storico",
        )!!
        assertEquals("https://h/scarica-x/spotlive-player-test.apk", i.url); assertEquals("stabile", i.canale); assertFalse(i.automatico)
    }

    @Test
    fun rifiutaSenzaShaOHttp() {
        assertNull(RegolaAggiornamento.leggi(o("""{"versionCode":6,"url":"a.apk","sha256":"zz"}"""), "https://h/", "server"))
        assertNull(RegolaAggiornamento.leggi(o("""{"versionCode":6,"url":"http://h/a.apk","sha256":"$sha"}"""), "https://h/", "server"))
    }

    private fun d(i: IstruzioneAndroid, forzato: Boolean = false, silenzioso: Boolean = true, noto: Boolean = true, acceso: Boolean = false, attesa: Long = 0, ora: Int = 12) =
        RegolaAggiornamento.decidi(i, 16, forzato, silenzioso, noto, acceso, attesa, ora)

    @Test
    fun decisioni() {
        assertEquals(Decisione.NON_NECESSARIO, d(ist(16)))
        assertEquals(Decisione.RITORNO_NON_POSSIBILE, d(ist(15)))
        assertEquals(Decisione.NON_NECESSARIO, d(ist(6, fonte = "storico")))
        assertEquals(Decisione.NON_AUTOMATICO, d(ist(17, auto = false)))
        assertEquals(Decisione.INSTALLA, d(ist(17, auto = false), forzato = true, silenzioso = false))
        assertEquals(Decisione.ATTESA_TECNICO, d(ist(17), silenzioso = false))
        assertEquals(Decisione.INSTALLA, d(ist(17), acceso = false))
        assertEquals(Decisione.ATTENDI_FUORI_ORARIO, d(ist(17), acceso = true))
        assertEquals(Decisione.ATTENDI_FUORI_ORARIO, d(ist(17), acceso = true, attesa = 25 * 3_600_000L, ora = 12))
        assertEquals(Decisione.INSTALLA, d(ist(17), acceso = true, attesa = 25 * 3_600_000L, ora = 3))
        assertEquals(Decisione.INSTALLA, d(ist(17), noto = false, acceso = true))
    }

    @Test
    fun firma() {
        val s = setOf(cert)
        assertTrue(RegolaAggiornamento.firmaValida("net.spotlive.player", s, s, cert))
        assertTrue(RegolaAggiornamento.firmaValida("net.spotlive.player", s, s, null))
        assertFalse(RegolaAggiornamento.firmaValida("net.altro", s, s, null))
        assertFalse(RegolaAggiornamento.firmaValida("net.spotlive.player", setOf("b".repeat(64)), s, null))
        assertFalse(RegolaAggiornamento.firmaValida("net.spotlive.player", setOf("b".repeat(64)), setOf("b".repeat(64)), null))
        assertFalse(RegolaAggiornamento.firmaValida("net.spotlive.player", s, s, "c".repeat(64)))
        assertFalse(RegolaAggiornamento.firmaValida("net.spotlive.player", emptySet(), emptySet(), null))
    }
}
