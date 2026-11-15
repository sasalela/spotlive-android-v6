// SVILUPPO-20261114-02: archivio legacy con fornitore di chiave finto (Robolectric non ha AndroidKeyStore).
package net.spotlive.player.piattaforma

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.spotlive.player.sistema.Diario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22], application = Application::class)
class ArchivioLegacyTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()
    private val chiave = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val altra = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val segreto = "segreto-123_ü"

    private fun prefs() = c.getSharedPreferences(ArchivioSicuro.NOME_FILE, Context.MODE_PRIVATE)
    private fun conChiave() = ArchivioSicuro(c) { FornitoreChiave { chiave } }
    private fun senzaKeystore() = ArchivioSicuro(c) { FornitoreChiave { throw java.security.KeyStoreException("AndroidKeyStore assente") } }

    @Before fun pulisci() {
        prefs().edit().clear().commit()
        Variante.archivioCifrato = null
    }

    @Test fun cifraturaPura() {
        val s = CifraturaArchivio.codifica(chiave, "token", segreto)
        assertTrue(s.startsWith(CifraturaArchivio.CIFRATO))
        assertFalse(s.contains("segreto"))
        assertEquals(segreto, CifraturaArchivio.decifra(chiave, "token", s))
        assertNull(CifraturaArchivio.decifra(altra, "token", s)) // chiave diversa
        assertNull(CifraturaArchivio.decifra(chiave, "taurus_password_terminale", s)) // valore spostato su altra chiave
        assertNull(CifraturaArchivio.decifra(null, "token", s)) // cifrato ma chiave non disponibile
        val manomesso = s.dropLast(2) + if (s.endsWith("00")) "11" else "00"
        assertNull(CifraturaArchivio.decifra(chiave, "token", manomesso))
        assertNull(CifraturaArchivio.decifra(chiave, "token", "c1:zz"))
        assertNull(CifraturaArchivio.decifra(chiave, "token", null))
        assertEquals("x", CifraturaArchivio.decifra(chiave, "token", "p1:x"))
        assertEquals("p1:x", CifraturaArchivio.codifica(null, "token", "x"))
        // IV casuale: due cifrature dello stesso valore sono diverse.
        assertFalse(s == CifraturaArchivio.codifica(chiave, "token", segreto))
    }

    @Test fun archivioCifratoConStessaApi() {
        val a = conChiave()
        assertEquals(true, Variante.archivioCifrato)
        assertEquals(Pair(false, null), a.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        a.scrivi(ArchivioSicuro.CHIAVE_TOKEN, segreto)
        a.salvaPasswordTerminale("pw-terminale")
        val grezzo = prefs().getString("token", null)!!
        assertTrue(grezzo.startsWith("c1:"))
        assertFalse(grezzo.contains("segreto"))
        assertFalse(prefs().getString("taurus_password_terminale", null)!!.contains("pw-terminale"))
        // Nuova istanza (riavvio dell'app) con la stessa chiave.
        val b = conChiave()
        assertEquals(Pair(true, segreto), b.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        assertEquals("pw-terminale", b.passwordTerminale())
        assertEquals(Pair(false, null), b.leggi("altra"))
        assertTrue(runCatching { b.scrivi("altra", "x") }.isFailure)
        assertTrue(runCatching { b.cancella("altra") }.isFailure)
        assertTrue(runCatching { b.salvaPasswordTerminale("") }.isFailure)
        b.cancella(ArchivioSicuro.CHIAVE_TOKEN)
        b.cancellaPasswordTerminale()
        assertEquals(Pair(false, null), b.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        assertNull(b.passwordTerminale())
    }

    @Test fun ripiegoInChiaroSenzaCrash() {
        val a = senzaKeystore()
        assertEquals(false, Variante.archivioCifrato)
        assertFalse(a.cifrato)
        a.scrivi(ArchivioSicuro.CHIAVE_TOKEN, segreto)
        assertEquals(Pair(true, segreto), a.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        assertEquals("p1:$segreto", prefs().getString("token", null))
        assertTrue(Diario.ultimi(c).any { "Keystore non disponibile" in it })
        // Il Keystore torna: il valore in chiaro resta leggibile e alla prossima scrittura si cifra.
        val b = conChiave()
        assertEquals(Pair(true, segreto), b.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        b.scrivi(ArchivioSicuro.CHIAVE_TOKEN, segreto)
        assertTrue(prefs().getString("token", null)!!.startsWith("c1:"))
    }

    @Test fun costruttoreRealeNonVaMaiInCrash() {
        // Nessun AndroidKeyStore in Robolectric: deve finire nel ripiego, senza eccezioni.
        val a = ArchivioSicuro(c)
        a.scrivi(ArchivioSicuro.CHIAVE_TOKEN, "t")
        assertEquals(Pair(true, "t"), a.leggi(ArchivioSicuro.CHIAVE_TOKEN))
        assertEquals(a.cifrato, Variante.archivioCifrato)
    }
}
