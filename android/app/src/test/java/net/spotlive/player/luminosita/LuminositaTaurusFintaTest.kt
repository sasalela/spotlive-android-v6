package net.spotlive.player.luminosita

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Servizio Taurus finto su 127.0.0.1 (UDP 16601 + TCP senza TLS): prova il dialogo completo
 * scoperta → login → lettura → scrittura volatile → rilettura, e i codici d'errore.
 */
class LuminositaTaurusFintaTest {
    private val password = "giusta"
    @Volatile private var ratio = 70
    @Volatile var scritture = 0
    @Volatile var ultimaSolidity: Boolean? = null
    private val tcp = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    private val udp = DatagramSocket(ProtocolloTaurus.PORTA_SCOPERTA, InetAddress.getByName("127.0.0.1"))
    @Volatile private var attivo = true

    init {
        thread(isDaemon = true) {
            while (attivo) {
                val buf = ByteArray(1024)
                val p = DatagramPacket(buf, buf.size)
                runCatching { udp.receive(p) }.onFailure { return@thread }
                val corpo = """{"sn":"SNFINTO","tcpPort":${tcp.localPort},"privacy":false}""".toByteArray()
                val r = pacchetto(0xFFFFFFFFL, ProtocolloTaurus.SCOPERTA, 1, 0, corpo)
                udp.send(DatagramPacket(r, r.size, p.address, p.port))
            }
        }
        thread(isDaemon = true) {
            while (attivo) {
                val s = runCatching { tcp.accept() }.getOrNull() ?: return@thread
                thread(isDaemon = true) {
                    s.use {
                        val inp = it.getInputStream(); val out = it.getOutputStream()
                        var loggato = false
                        while (true) {
                            val testa = ByteArray(24); var n = 0
                            while (n < 24) { val k = inp.read(testa, n, 24 - n); if (k < 0) return@thread; n += k }
                            val len = ByteBuffer.wrap(testa).order(ByteOrder.LITTLE_ENDIAN).getInt(16)
                            val corpo = ByteArray(len); n = 0
                            while (n < len) { val k = inp.read(corpo, n, len - n); if (k < 0) return@thread; n += k }
                            val p = ProtocolloTaurus.decodifica(testa + corpo)
                            val action = testa[12].toInt()
                            val (stato, risposta) = when {
                                p.what == 0 -> {
                                    val o = Json.parseToJsonElement(p.corpo).jsonObject
                                    loggato = o["password"]?.jsonPrimitive?.content == password && o["sn"]?.jsonPrimitive?.content == "SNFINTO"
                                    0 to """{"logined":$loggato}"""
                                }
                                !loggato -> 0x101 to ""
                                p.what == 24 && action == 5 -> 0 to """{"brightness":${ratio * 255 / 100},"ratio":$ratio,"solidity":false,"orderId":-1}"""
                                p.what == 24 && action == 4 -> {
                                    val o = Json.parseToJsonElement(p.corpo).jsonObject
                                    ratio = o["ratio"]!!.jsonPrimitive.content.toInt(); scritture++
                                    ultimaSolidity = o["solidity"]!!.jsonPrimitive.content.toBoolean()
                                    0 to ""
                                }
                                else -> 0x2 to ""
                            }
                            out.write(pacchetto(p.sequenza, ProtocolloTaurus.RISPOSTA, p.what, stato, risposta.toByteArray())); out.flush()
                        }
                    }
                }
            }
        }
    }

    private fun pacchetto(seq: Long, tipo: Int, what: Int, stato: Int, corpo: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(24 + corpo.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(ProtocolloTaurus.FLAG); b.putInt(seq.toInt()); b.putShort(tipo.toShort()); b.putShort(what.toShort())
        b.putShort(stato.toShort()); b.put(0); b.put(0); b.putInt(corpo.size); b.put(0); b.put(0)
        val c = ProtocolloTaurus.checksum(b.array()); b.putShort(c.toShort()); b.put(corpo)
        return b.array()
    }

    @After
    fun chiudi() { attivo = false; udp.close(); tcp.close() }

    @Test
    fun dialogoCompletoEErrori() {
        val t = LuminositaTaurus(timeoutMs = 2000)
        assertEquals(70, t.leggi(password))
        val e = t.imposta(40, password)
        assertTrue(e.scritto); assertEquals(40, e.lettaPct); assertEquals(1, scritture); assertEquals(false, ultimaSolidity)
        // Stesso valore: rilegge e non riscrive.
        val e2 = t.imposta(40, password)
        assertFalse(e2.scritto); assertEquals(1, scritture)
        val errata = runCatching { t.imposta(50, "sbagliata") }.exceptionOrNull() as ErroreLuminosita
        assertEquals("password_terminale_errata", errata.codice)
        assertEquals(1, scritture)
        val mancante = runCatching { t.imposta(50, null) }.exceptionOrNull() as ErroreLuminosita
        assertEquals("password_terminale_mancante", mancante.codice)
        // Il messaggio d'errore non contiene mai la password.
        assertFalse(errata.message!!.contains("sbagliata"))
    }

    @Test
    fun servizioAssente() {
        attivo = false; udp.close(); tcp.close()
        Thread.sleep(100)
        val e = runCatching { LuminositaTaurus(timeoutMs = 500).leggi(password) }.exceptionOrNull() as ErroreLuminosita
        assertEquals("servizio_non_raggiungibile", e.codice)
    }
}
