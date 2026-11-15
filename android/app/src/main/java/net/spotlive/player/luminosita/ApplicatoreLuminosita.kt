package net.spotlive.player.luminosita

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.spotlive.player.BuildConfig
import net.spotlive.player.piattaforma.ArchivioSicuro
import net.spotlive.player.sistema.Diario
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Applica la regola di luminosità del manifest alla Taurus su cui gira l'app (1.0.15).
 *
 * - Campo `luminosita` assente nel manifest = gestione spenta: nessuna chiamata al LED, mai.
 * - Ogni minuto calcola il valore atteso (sole o orari fissi, 4 gradini in 20 minuti); scrive solo se cambia,
 *   più una riaffermazione ogni 15 minuti (prima rilegge e riscrive solo se ViPlex l'ha cambiato).
 * - Se il player web applica già lui (metodo ponte `luminosita` usato negli ultimi 5 minuti), qui si sta fermi:
 *   mai due applicatori sullo stesso LED.
 * - La password del terminale si ritira una sola volta dal server e resta solo nell'ArchivioSicuro.
 */
class ApplicatoreLuminosita(
    private val contesto: Context,
    private val archivio: ArchivioSicuro,
    private val taurus: LuminositaTaurus = LuminositaTaurus(),
) {
    private val esecutore = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "spotlive-luminosita").apply { isDaemon = true } }
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = contesto.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    @Volatile private var regola: RegolaLuminosita? = null
    @Volatile private var fuso: String = "Europe/Rome"
    @Volatile private var ultimaChiamataWeb = 0L
    private var ultimoScritto: Int? = null
    private var ultimaAffermazione = 0L
    private var prossimoTentativo = 0L
    private var erroriDiFila = 0
    @Volatile private var stato: Map<String, Any?> = mapOf("attiva" to false)
    private var avviato = false

    @Synchronized
    fun avvia() {
        if (avviato) return
        avviato = true
        // Ultima regola nota: dopo un riavvio senza rete si continua con quella (il campo assente la cancella).
        prefs.getString("regola", null)?.let { s ->
            regola = runCatching { CalcoloLuminosita.daManifest(json.parseToJsonElement(s).jsonObject) }.getOrNull()
            fuso = prefs.getString("fuso", fuso) ?: fuso
        }
        esecutore.scheduleWithFixedDelay({ runCatching { giro() } }, 20, 60, TimeUnit.SECONDS)
    }

    /** Chiamato a ogni rilettura nativa del manifest. luminosita = null significa interruttore spento. */
    fun daManifest(luminosita: JsonObject?, fusoSchermo: String?, passwordDaRitirare: Boolean) {
        esecutore.execute { runCatching { applicaManifest(luminosita, fusoSchermo, passwordDaRitirare) } }
    }

    private fun applicaManifest(luminosita: JsonObject?, fusoSchermo: String?, passwordDaRitirare: Boolean) {
        val nuova = CalcoloLuminosita.daManifest(luminosita)
        val prima = regola
        regola = nuova
        if (!fusoSchermo.isNullOrBlank()) fuso = fusoSchermo
        prefs.edit().apply {
            if (nuova == null) remove("regola") else putString("regola", luminosita.toString())
            putString("fuso", fuso)
        }.apply()
        if ((prima == null) != (nuova == null)) {
            Diario.evento(contesto, if (nuova == null) "luminosità: gestione spenta, il LED non viene più toccato" else "luminosità: gestione attiva (${nuova.modo}, giorno ${nuova.giornoPct}% notte ${nuova.nottePct}%)")
            ultimoScritto = null
            prossimoTentativo = 0
            if (nuova == null) stato = mapOf("attiva" to false)
        }
        if (passwordDaRitirare && luminosita != null) runCatching { ritiraPassword() }
        if (nuova != null && prima != nuova) runCatching { giro() }
    }

    /** Il player web dichiara di applicare lui (metodo ponte `luminosita`): l'applicatore nativo si ferma per 5 minuti. */
    fun segnaChiamataWeb() { ultimaChiamataWeb = System.currentTimeMillis() }

    /** Scrittura diretta (ponte o prova manuale). Bloccante: da chiamare fuori dal thread principale. */
    fun impostaOra(pct: Int): Map<String, Any?> =
        esecutore.submit<Map<String, Any?>> {
            try {
                val e = taurus.imposta(pct, archivio.passwordTerminale())
                mapOf("esito" to "ok", "lettaPct" to e.lettaPct)
            } catch (e: ErroreLuminosita) {
                mapOf("esito" to "errore", "codice" to e.codice)
            }
        }.get(30, TimeUnit.SECONDS)

    fun leggiOra(): Map<String, Any?> =
        esecutore.submit<Map<String, Any?>> {
            try {
                mapOf("esito" to "ok", "lettaPct" to taurus.leggi(archivio.passwordTerminale()))
            } catch (e: ErroreLuminosita) {
                mapOf("esito" to "errore", "codice" to e.codice)
            }
        }.get(30, TimeUnit.SECONDS)

    /** Prova manuale dalla Configurazione: imposta pct, aspetta, poi rimette il valore letto prima. */
    fun prova(pct: Int, durataMs: Long, fine: (String) -> Unit) {
        esecutore.execute {
            val pw = archivio.passwordTerminale()
            val testo = try {
                val prima = taurus.leggi(pw)
                val e = taurus.imposta(pct, pw)
                Diario.evento(contesto, "luminosità: prova manuale $pct% (prima ${prima ?: "?"}%)")
                Thread.sleep(durataMs)
                if (prima != null) taurus.imposta(prima, pw)
                "Prova riuscita: ${e.lettaPct ?: pct}% per ${durataMs / 1000} s, poi rimesso ${prima ?: "?"}%"
            } catch (e: ErroreLuminosita) {
                "Prova non riuscita: ${e.codice}"
            } catch (e: Exception) {
                "Prova non riuscita: ${e.javaClass.simpleName}"
            }
            fine(testo)
        }
    }

    fun stato(): Map<String, Any?> = stato + ("passwordPresente" to (archivio.passwordTerminale() != null))

    private fun giro() {
        val r = regola ?: return // spento: nessuna chiamata
        val ora = System.currentTimeMillis()
        if (ora - ultimaChiamataWeb < 5 * 60_000L) return
        if (ora < prossimoTentativo) return
        val atteso = CalcoloLuminosita.attesa(r, fuso, ora)
        val serve = atteso.pct != ultimoScritto || ora - ultimaAffermazione >= 15 * 60_000L
        if (!serve) return
        try {
            val e = taurus.imposta(atteso.pct, archivio.passwordTerminale())
            if (e.scritto || ultimoScritto != atteso.pct) {
                Diario.evento(contesto, "luminosità ${atteso.pct}% (${atteso.fase}${if (atteso.pct != atteso.finale) ", passaggio verso ${atteso.finale}%" else ""}) riletta ${e.lettaPct ?: "?"}%")
            }
            ultimoScritto = atteso.pct
            ultimaAffermazione = ora
            erroriDiFila = 0
            stato = mapOf(
                "attiva" to true, "applicataPct" to atteso.pct, "fase" to atteso.fase, "alle" to ora,
                "lettaPct" to e.lettaPct, "tramite" to "taurus", "errore" to null,
                "alba" to atteso.albaMs, "tramonto" to atteso.tramontoMs,
            )
        } catch (e: ErroreLuminosita) {
            erroriDiFila++
            val attesa = when (e.codice) {
                // Mai tentativi a raffica con una password sbagliata: si riprova ogni 30 minuti.
                "password_terminale_errata" -> 30 * 60_000L
                "password_terminale_mancante" -> 5 * 60_000L
                else -> if (erroriDiFila >= 3) 15 * 60_000L else 60_000L
            }
            prossimoTentativo = ora + attesa
            val tramite = if (e.codice == "servizio_non_raggiungibile" && erroriDiFila >= 3) "non_supportato" else "taurus"
            if (stato["errore"].let { it as? Map<*, *> }?.get("codice") != e.codice) {
                Diario.evento(contesto, "luminosità: errore ${e.codice}")
            }
            stato = mapOf(
                "attiva" to true, "applicataPct" to ultimoScritto, "fase" to atteso.fase, "alle" to ora,
                "lettaPct" to null, "tramite" to tramite, "errore" to mapOf("codice" to e.codice, "alle" to ora),
            )
        }
    }

    /**
     * Ritiro unico della password del terminale (richiesta al server nel CONTRATTO-PLAYER):
     * GET /api/v1/player/luminosita/password-terminale → {"password": "..."}; poi POST …/ritirata e il server la cancella.
     * La password non passa mai dal player web, non si registra e non torna nello stato.
     */
    private fun ritiraPassword() {
        val token = archivio.leggi(ArchivioSicuro.CHIAVE_TOKEN).second ?: return
        val origine = origine() ?: return
        val url = "$origine/api/v1/player/luminosita/password-terminale"
        val richiesta = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json").build()
        val password = client.newCall(richiesta).execute().use { r ->
            if (r.code == 404 || r.code == 410) return
            if (!r.isSuccessful) { Diario.evento(contesto, "luminosità: ritiro password non riuscito (HTTP ${r.code})"); return }
            val o = runCatching { json.parseToJsonElement(r.body?.string() ?: "").jsonObject }.getOrNull()
            o?.get("password")?.jsonPrimitive?.contentOrNull
        } ?: return
        archivio.salvaPasswordTerminale(password)
        val conferma = Request.Builder().url("$url/ritirata").header("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        runCatching { client.newCall(conferma).execute().close() }
        Diario.evento(contesto, "luminosità: password del terminale ricevuta e salvata cifrata")
        prossimoTentativo = 0
        taurus.dimentica()
    }

    private fun origine(): String? {
        val u = Uri.parse(BuildConfig.URL_PLAYER)
        if (u.scheme != "https" || u.authority.isNullOrEmpty()) return null
        return "https://${u.authority}"
    }

    companion object {
        private const val PREF = "spotlive_luminosita"
    }
}
