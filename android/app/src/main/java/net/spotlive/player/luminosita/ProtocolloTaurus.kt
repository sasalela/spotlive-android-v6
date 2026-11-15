package net.spotlive.player.luminosita

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pacchetto del servizio di gestione Taurus (porta TCP 16606, scoperta UDP 16601).
 * Ricavato dalla libreria MIT @novastar/taurus 0.4.1 (non ufficiale). Modulo puro: provato con
 * vettori generati dalla libreria originale (ProtocolloTaurusTest).
 *
 * Intestazione di 24 byte, little endian:
 *  flag u32 (0x4E4F5641) · sequenza u32 · tipo pacchetto u16 · what u16 ·
 *  richiesta: action u8, type u8, 2 riservati  |  risposta: status u16, action u8, type u8 ·
 *  lunghezza corpo u32 · encodeType u8 · 1 riservato · checksum u16 (somma dei byte con segno 0..21) · corpo JSON
 */
object ProtocolloTaurus {
    const val FLAG = 0x4E4F5641
    const val RICHIESTA = 21073
    const val RISPOSTA = 21072
    const val SCOPERTA = 34901
    const val INTESTAZIONE = 24
    const val PORTA_GESTIONE = 16606
    const val PORTA_SCOPERTA = 16601

    data class Comando(val what: Int, val type: Int, val action: Int)

    val LOGIN = Comando(0, 0, 0)
    val LEGGI_LUMINOSITA = Comando(24, 1, 5)
    val IMPOSTA_LUMINOSITA = Comando(24, 1, 4)

    data class Pacchetto(val sequenza: Long, val tipo: Int, val what: Int, val stato: Int, val corpo: String)

    fun richiesta(sequenza: Long, c: Comando, corpo: String = ""): ByteArray =
        costruisci(sequenza, RICHIESTA, c.what, c.action, c.type, corpo.toByteArray(Charsets.UTF_8))

    fun scoperta(cifraturaSupportata: Boolean = true): ByteArray =
        costruisci(0xFFFFFFFFL, SCOPERTA, 129, if (cifraturaSupportata) 1 else 0, 0, ByteArray(0))

    private fun costruisci(seq: Long, tipo: Int, what: Int, action: Int, type: Int, corpo: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(INTESTAZIONE + corpo.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(FLAG)
        b.putInt(seq.toInt())
        b.putShort(tipo.toShort())
        b.putShort(what.toShort())
        b.put(action.toByte())
        b.put(type.toByte())
        b.putShort(0)
        b.putInt(corpo.size)
        b.put(0) // encodeType
        b.put(0)
        val a = b.array()
        b.putShort(checksum(a).toShort())
        b.put(corpo)
        return b.array()
    }

    fun checksum(intestazione: ByteArray): Int {
        var s = 0
        for (i in 0 until 22) s += intestazione[i].toInt() // byte con segno, come l'originale
        return s and 0xFFFF
    }

    /** Lunghezza totale del pacchetto se l'intestazione è completa, altrimenti null. */
    fun lunghezza(buf: ByteArray, n: Int = buf.size): Int? {
        if (n < INTESTAZIONE) return null
        val b = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getInt(0) != FLAG) throw IllegalArgumentException("flag Taurus non valido")
        val len = b.getInt(16)
        if (len < 0 || len > 1_000_000) throw IllegalArgumentException("lunghezza Taurus non valida")
        return INTESTAZIONE + len
    }

    fun decodifica(buf: ByteArray): Pacchetto {
        val tot = lunghezza(buf) ?: throw IllegalArgumentException("pacchetto Taurus incompleto")
        if (buf.size < tot) throw IllegalArgumentException("pacchetto Taurus incompleto")
        val b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val atteso = b.getShort(22).toInt() and 0xFFFF
        if (checksum(buf) != atteso) throw IllegalArgumentException("checksum Taurus non valido")
        return Pacchetto(
            sequenza = b.getInt(4).toLong() and 0xFFFFFFFFL,
            tipo = b.getShort(8).toInt() and 0xFFFF,
            what = b.getShort(10).toInt() and 0xFFFF,
            stato = b.getShort(12).toInt() and 0xFFFF,
            corpo = String(buf, INTESTAZIONE, tot - INTESTAZIONE, Charsets.UTF_8),
        )
    }

    /** Corpo JSON del login. La password non va mai nei log: questa stringa non si registra. */
    fun corpoLogin(sn: String, password: String, clientId: String): String =
        kotlinx.serialization.json.buildJsonObject {
            put("sn", kotlinx.serialization.json.JsonPrimitive(sn))
            put("username", kotlinx.serialization.json.JsonPrimitive("admin"))
            put("password", kotlinx.serialization.json.JsonPrimitive(password))
            put("loginType", kotlinx.serialization.json.JsonPrimitive(0))
            put(
                "source",
                kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive(1))
                    put("platform", kotlinx.serialization.json.JsonPrimitive(2))
                    put("platformVersion", kotlinx.serialization.json.JsonPrimitive("3.6.0"))
                },
            )
            put("clientId", kotlinx.serialization.json.JsonPrimitive(clientId))
            put("clientName", kotlinx.serialization.json.JsonPrimitive("SpotLive"))
        }.toString()

    fun corpoImposta(pct: Int): String = "{\"ratio\":$pct,\"solidity\":false,\"orderId\":-1}"
}
