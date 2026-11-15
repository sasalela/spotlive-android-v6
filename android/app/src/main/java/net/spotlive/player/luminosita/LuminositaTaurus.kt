package net.spotlive.player.luminosita

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

/** Errore con codice del contratto (PROGETTO §1.6). Il messaggio non contiene mai la password. */
class ErroreLuminosita(val codice: String, messaggio: String) : Exception(messaggio)

data class EsitoLuminosita(val lettaPct: Int?, val scritto: Boolean)

/**
 * Luminosità della scheda ricevente tramite il servizio locale della Taurus (lo stesso di ViPlex).
 * Solo la Taurus su cui gira l'app: si parla solo con 127.0.0.1 o con un indirizzo del dispositivo stesso,
 * mai con altre Taurus della rete. Scrittura volatile (solidity=false): al riavvio torna il valore di ViPlex.
 */
class LuminositaTaurus(private val timeoutMs: Int = 5000) {
    data class Servizio(val sn: String, val porta: Int, val privacy: Boolean, val indirizzo: InetAddress)

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile var servizio: Servizio? = null
        private set

    fun scopri(): Servizio {
        servizio?.let { return it }
        val propri = indirizziPropri()
        val destinazioni = mutableListOf<InetAddress>(InetAddress.getByName("127.0.0.1"))
        destinazioni += broadcastPropri()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 700
            val dati = ProtocolloTaurus.scoperta(true)
            for (d in destinazioni) {
                runCatching { s.send(DatagramPacket(dati, dati.size, d, ProtocolloTaurus.PORTA_SCOPERTA)) }
                val fine = System.currentTimeMillis() + 1200
                while (System.currentTimeMillis() < fine) {
                    val buf = ByteArray(4096)
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: java.net.SocketTimeoutException) { break }
                    // Solo risposte del dispositivo stesso.
                    if (!p.address.isLoopbackAddress && p.address !in propri) continue
                    val pk = runCatching { ProtocolloTaurus.decodifica(buf.copyOf(p.length)) }.getOrNull() ?: continue
                    if (pk.tipo != ProtocolloTaurus.SCOPERTA || pk.what != 1) continue
                    val o = runCatching { json.parseToJsonElement(pk.corpo).jsonObject }.getOrNull() ?: continue
                    val sn = o["sn"]?.jsonPrimitive?.contentOrNull ?: continue
                    val porta = o["tcpPort"]?.jsonPrimitive?.intOrNull ?: ProtocolloTaurus.PORTA_GESTIONE
                    val privacy = o["privacy"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull?.let { n -> n != 0 }) } ?: true
                    val trovato = Servizio(sn, porta, privacy, p.address)
                    servizio = trovato
                    return trovato
                }
            }
        }
        throw ErroreLuminosita("servizio_non_raggiungibile", "nessuna risposta del servizio Taurus su UDP 16601")
    }

    fun dimentica() { servizio = null }

    /** Legge la luminosità attuale in percentuale. */
    fun leggi(password: String?): Int? = sessione(password) { it.leggi() }

    /** Imposta (volatile) e rilegge. Se la rilettura differisce di più di 1 punto: rilettura_diversa. */
    fun imposta(pct: Int, password: String?): EsitoLuminosita = sessione(password) { c ->
        val v = pct.coerceIn(5, 100)
        val prima = c.leggi()
        if (prima != null && kotlin.math.abs(prima - v) <= 1) return@sessione EsitoLuminosita(prima, false)
        c.json(ProtocolloTaurus.IMPOSTA_LUMINOSITA, ProtocolloTaurus.corpoImposta(v))
        val letta = c.leggi()
        if (letta != null && kotlin.math.abs(letta - v) > 1) throw ErroreLuminosita("rilettura_diversa", "letto $letta% invece di $v%")
        EsitoLuminosita(letta, true)
    }

    private fun <T> sessione(password: String?, azione: (Connessione) -> T): T {
        if (password.isNullOrEmpty()) throw ErroreLuminosita("password_terminale_mancante", "password del terminale assente")
        val sv = scopri()
        val c = try {
            Connessione.apri(sv, timeoutMs)
        } catch (e: Exception) {
            dimentica()
            throw ErroreLuminosita("servizio_non_raggiungibile", "connessione al servizio Taurus non riuscita (${e.javaClass.simpleName})")
        }
        c.use {
            val r = try {
                it.richiesta(ProtocolloTaurus.LOGIN, ProtocolloTaurus.corpoLogin(sv.sn, password, java.util.UUID.randomUUID().toString()))
            } catch (e: Exception) {
                throw ErroreLuminosita("servizio_non_raggiungibile", "login senza risposta (${e.javaClass.simpleName})")
            }
            val o = runCatching { json.parseToJsonElement(r.corpo).jsonObject }.getOrNull()
            if (r.stato != 0 || o?.get("logined")?.jsonPrimitive?.booleanOrNull != true) {
                throw ErroreLuminosita("password_terminale_errata", "login Taurus rifiutato (stato ${r.stato})")
            }
            return azione(it)
        }
    }

    private class Connessione(private val socket: Socket) : AutoCloseable {
        private val ingresso: InputStream = socket.getInputStream()
        private val uscita: OutputStream = socket.getOutputStream()
        private var seq = 0L
        private val json = Json { ignoreUnknownKeys = true }

        fun richiesta(c: ProtocolloTaurus.Comando, corpo: String = ""): ProtocolloTaurus.Pacchetto {
            seq = (seq + 1) and 0xFFFFFFFFL
            uscita.write(ProtocolloTaurus.richiesta(seq, c, corpo))
            uscita.flush()
            while (true) {
                val p = leggiPacchetto()
                if (p.tipo == ProtocolloTaurus.RISPOSTA && p.sequenza == seq) return p
            }
        }

        fun json(c: ProtocolloTaurus.Comando, corpo: String = ""): JsonObject? {
            val p = richiesta(c, corpo)
            if (p.stato != 0) throw ErroreLuminosita("servizio_non_raggiungibile", "richiesta what=${c.what} rifiutata (stato ${p.stato})")
            return if (p.corpo.isBlank()) null else runCatching { json.parseToJsonElement(p.corpo).jsonObject }.getOrNull()
        }

        fun leggi(): Int? {
            val o = json(ProtocolloTaurus.LEGGI_LUMINOSITA) ?: return null
            return o["ratio"]?.jsonPrimitive?.doubleOrNull?.roundToInt()
        }

        private fun leggiPacchetto(): ProtocolloTaurus.Pacchetto {
            val testa = leggiEsatti(ProtocolloTaurus.INTESTAZIONE)
            val tot = ProtocolloTaurus.lunghezza(testa)!!
            val resto = leggiEsatti(tot - ProtocolloTaurus.INTESTAZIONE)
            return ProtocolloTaurus.decodifica(testa + resto)
        }

        private fun leggiEsatti(n: Int): ByteArray {
            val b = ByteArray(n)
            var letti = 0
            while (letti < n) {
                val k = ingresso.read(b, letti, n - letti)
                if (k < 0) throw java.io.EOFException("connessione chiusa")
                letti += k
            }
            return b
        }

        override fun close() { runCatching { socket.close() } }

        companion object {
            fun apri(sv: Servizio, timeoutMs: Int): Connessione {
                val grezzo = Socket()
                grezzo.connect(InetSocketAddress(sv.indirizzo, sv.porta), timeoutMs)
                grezzo.soTimeout = timeoutMs
                if (!sv.privacy) return Connessione(grezzo)
                // Il servizio locale usa un certificato autofirmato: si accetta SOLO perché il socket è verso
                // il dispositivo stesso (127.0.0.1 o un suo indirizzo), mai verso un altro host.
                check(sv.indirizzo.isLoopbackAddress || sv.indirizzo in indirizziPropri()) { "TLS senza verifica ammesso solo verso il dispositivo stesso" }
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(FiduciaLocale), null)
                val tls = ctx.socketFactory.createSocket(grezzo, sv.indirizzo.hostAddress, sv.porta, true) as SSLSocket
                tls.soTimeout = timeoutMs
                tls.startHandshake()
                return Connessione(tls)
            }
        }
    }

    private object FiduciaLocale : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun broadcastPropri(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.mapNotNull { a -> a.broadcast } }
    }.getOrDefault(emptyList())
}

internal fun indirizziPropri(): Set<InetAddress> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.toSet()
}.getOrDefault(emptySet())
