package net.spotlive.player.luminosita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Vettori generati con @novastar/taurus 0.4.1 (encodeTaurusRequest / encodeTaurusDiscoveryRequest). */
class ProtocolloTaurusTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun bytes(h: String) = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun scoperta() = assertEquals("41564f4effffffff55888100010000000000000000008f00", hex(ProtocolloTaurus.scoperta(true)))

    @Test
    fun leggiLuminosita() =
        assertEquals("41564f4e010000005152180005010000000000000000f601", hex(ProtocolloTaurus.richiesta(1, ProtocolloTaurus.LEGGI_LUMINOSITA)))

    @Test
    fun impostaLuminosita40() =
        assertEquals(
            "41564f4e0700000051521800040100002a000000000025027b22726174696f223a34302c22736f6c6964697479223a66616c73652c226f726465724964223a2d317d",
            hex(ProtocolloTaurus.richiesta(7, ProtocolloTaurus.IMPOSTA_LUMINOSITA, ProtocolloTaurus.corpoImposta(40))),
        )

    @Test
    fun loginConCorpo() =
        assertEquals("41564f4e020000005152000000000000070000000000e0017b2261223a317d", hex(ProtocolloTaurus.richiesta(2, ProtocolloTaurus.LOGIN, "{\"a\":1}")))

    @Test
    fun decodificaRisposta() {
        // Risposta: tipo 21072, stato 0 nel campo status.
        val req = ProtocolloTaurus.richiesta(9, ProtocolloTaurus.LEGGI_LUMINOSITA, "{\"ratio\":55}")
        req[8] = (21072 and 0xFF).toByte(); req[9] = (21072 shr 8).toByte()
        req[12] = 0; req[13] = 0
        val c = ProtocolloTaurus.checksum(req)
        req[22] = (c and 0xFF).toByte(); req[23] = (c shr 8).toByte()
        val p = ProtocolloTaurus.decodifica(req)
        assertEquals(9L, p.sequenza); assertEquals(ProtocolloTaurus.RISPOSTA, p.tipo); assertEquals(0, p.stato); assertEquals("{\"ratio\":55}", p.corpo)
    }

    @Test
    fun checksumErratoRifiutato() {
        val b = bytes("41564f4e010000005152180005010000000000000000f701")
        assertThrows(IllegalArgumentException::class.java) { ProtocolloTaurus.decodifica(b) }
    }

    @Test
    fun corpoLoginContieneSnENonAltro() {
        val c = ProtocolloTaurus.corpoLogin("SN1", "x", "id")
        assertEquals(true, c.contains("\"sn\":\"SN1\"") && c.contains("\"username\":\"admin\"") && c.contains("\"platformVersion\":\"3.6.0\""))
    }
}
