// SVILUPPO-20261114-02: fiducia TLS legacy. Senza rete: catena generata nel test (radice + foglia del pannello),
// più le radici ISRG vere di res/raw (impronte pubblicate su letsencrypt.org/certificates).
package net.spotlive.player.piattaforma

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TlsLegacyTest {
    private val host = "209-227-230-76.sslip.io"

    @Test fun catenaAccettataSoloConLAncora() {
        val (radice, foglia) = catena()
        val conAncora = Tls.trustManager(Tls.ancoreSistema() + radice)
        conAncora.checkServerTrusted(arrayOf(foglia), "ECDHE_RSA") // nessuna eccezione = accettata
        conAncora.checkServerTrusted(arrayOf(foglia, radice), "ECDHE_RSA")
        // Senza la radice (solo CA di sistema, o solo ISRG): rifiutata.
        assertRifiutata(Tls.trustManager(Tls.ancoreSistema()), foglia)
        assertRifiutata(Tls.trustManager(isrg()), foglia)
    }

    @Test fun catenaFirmataDaAltriRifiutata() {
        val (radice, _) = catena()
        val (_, fogliaEstranea) = catena()
        assertRifiutata(Tls.trustManager(listOf(radice)), fogliaEstranea)
    }

    @Test fun nomeHostVerificatoDaOkHttp() {
        val (_, foglia) = catena()
        assertTrue(OkHostnameVerifier.verify(host, foglia))
        assertFalse(OkHostnameVerifier.verify("altro.sslip.io", foglia))
        assertFalse(OkHostnameVerifier.verify("209-227-230-76.sslip.io.example.com", foglia))
    }

    @Test fun radiciIsrgVere() {
        val x1 = Tls.leggiPem(File("src/legacy/res/raw/isrg_root_x1.pem").inputStream())
        val x2 = Tls.leggiPem(File("src/legacy/res/raw/isrg_root_x2.pem").inputStream())
        assertEquals(1, x1.size)
        assertEquals(1, x2.size)
        assertTrue(x1[0].subjectX500Principal.name.contains("ISRG Root X1"))
        assertTrue(x2[0].subjectX500Principal.name.contains("ISRG Root X2"))
        assertEquals("96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6", Tls.impronta(x1[0]))
        assertEquals("69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470", Tls.impronta(x2[0]))
        val tm = Tls.trustManager(Tls.ancoreSistema() + x1 + x2)
        assertTrue(tm.acceptedIssuers.any { it == x1[0] })
        assertTrue(tm.acceptedIssuers.any { it == x2[0] })
    }

    @Test fun soloTlsModernoNienteChiaro() {
        val client = Tls.applica(OkHttpClient.Builder()).build()
        assertEquals(listOf(ConnectionSpec.MODERN_TLS), client.connectionSpecs)
        assertFalse(ConnectionSpec.CLEARTEXT in client.connectionSpecs)
        assertTrue(Tls.socketFactory() != null)
        assertEquals(null, Tls.fogliaVerificata(host)) // nessuna verifica nativa fatta
    }

    private fun isrg() =
        Tls.leggiPem(File("src/legacy/res/raw/isrg_root_x1.pem").inputStream()) +
            Tls.leggiPem(File("src/legacy/res/raw/isrg_root_x2.pem").inputStream())

    private fun assertRifiutata(tm: javax.net.ssl.X509TrustManager, foglia: X509Certificate) {
        val esito = runCatching { tm.checkServerTrusted(arrayOf(foglia), "ECDHE_RSA") }
        assertTrue("catena accettata per errore", esito.exceptionOrNull() is CertificateException)
    }

    // ---- certificati X.509 v3 generati con sola JDK (DER scritto a mano, nessuna dipendenza) ----

    private fun catena(): Pair<X509Certificate, X509Certificate> {
        val kRadice = chiavi()
        val kFoglia = chiavi()
        val radice = certificato("SpotLive Test Root", "SpotLive Test Root", kRadice, kRadice.private, ca = true)
        val foglia = certificato(host, "SpotLive Test Root", kFoglia, kRadice.private, ca = false)
        return radice to foglia
    }

    private fun chiavi(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun certificato(cn: String, emittente: String, k: KeyPair, firmaCon: PrivateKey, ca: Boolean): X509Certificate {
        val algoritmo = seq(oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00))
        val ora = System.currentTimeMillis()
        val estensioni = if (ca) {
            listOf(
                est("2.5.29.19", true, seq(byteArrayOf(0x01, 0x01, 0xFF.toByte()))),
                est("2.5.29.15", true, der(0x03, byteArrayOf(0x01, 0x06))),
            )
        } else {
            listOf(
                est("2.5.29.19", true, seq()),
                est("2.5.29.15", true, der(0x03, byteArrayOf(0x05, 0xA0.toByte()))),
                est("2.5.29.37", false, seq(oid("1.3.6.1.5.5.7.3.1"))),
                est("2.5.29.17", false, seq(der(0x82, cn.toByteArray(Charsets.US_ASCII)))),
            )
        }
        val tbs = seq(
            der(0xA0, der(0x02, byteArrayOf(0x02))),
            der(0x02, BigInteger.valueOf(ora).toByteArray()),
            algoritmo,
            nome(emittente),
            seq(tempo(Date(ora - 3_600_000L)), tempo(Date(ora + 30L * 86_400_000L))),
            nome(cn),
            k.public.encoded,
            der(0xA3, seq(*estensioni.toTypedArray())),
        )
        val firma = Signature.getInstance("SHA256withRSA").apply { initSign(firmaCon); update(tbs) }.sign()
        val cert = seq(tbs, algoritmo, der(0x03, byteArrayOf(0x00) + firma))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private fun nome(cn: String) = seq(der(0x31, seq(oid("2.5.4.3"), der(0x0C, cn.toByteArray(Charsets.UTF_8)))))

    private fun est(id: String, critica: Boolean, valore: ByteArray) =
        if (critica) seq(oid(id), byteArrayOf(0x01, 0x01, 0xFF.toByte()), der(0x04, valore))
        else seq(oid(id), der(0x04, valore))

    private fun tempo(d: Date): ByteArray {
        val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return der(0x17, f.format(d).toByteArray(Charsets.US_ASCII))
    }

    private fun oid(s: String): ByteArray {
        val p = s.split('.').map { it.toLong() }
        val corpo = mutableListOf((p[0] * 40 + p[1]).toByte())
        for (v in p.drop(2)) {
            val gruppi = mutableListOf<Byte>()
            var x = v
            gruppi += (x and 0x7F).toByte()
            x = x shr 7
            while (x > 0) {
                gruppi += ((x and 0x7F) or 0x80).toByte()
                x = x shr 7
            }
            corpo += gruppi.reversed()
        }
        return der(0x06, corpo.toByteArray())
    }

    private fun seq(vararg parti: ByteArray) = der(0x30, *parti)

    private fun der(tag: Int, vararg parti: ByteArray): ByteArray {
        val corpo = parti.fold(ByteArray(0)) { a, b -> a + b }
        val n = corpo.size
        val lunghezza = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + lunghezza + corpo
    }
}
