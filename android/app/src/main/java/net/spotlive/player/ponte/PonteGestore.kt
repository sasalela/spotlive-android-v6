package net.spotlive.player.ponte

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.webkit.WebView
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.spotlive.player.aggiornamenti.AggiornatoreApk
import net.spotlive.player.sistema.Configurazione
import net.spotlive.player.sistema.Diario
import net.spotlive.player.sistema.GestoreDispositivo
import net.spotlive.player.sistema.PausaAssistenza
import net.spotlive.player.sistema.RiavvioActivity
import net.spotlive.player.BuildConfig
import net.spotlive.player.MainActivity
import net.spotlive.player.cache.CacheNativa
import net.spotlive.player.piattaforma.ArchivioSicuro
import net.spotlive.player.piattaforma.IstantaneaSchermo

class PonteGestore(
    private val contesto: Context,
    private val cache: CacheNativa,
    private val archivio: ArchivioSicuro,
    private var activity: Activity? = null,
    private var webView: WebView? = null,
) {
    fun collega(activity: Activity, webView: WebView) {
        this.activity = activity
        this.webView = webView
    }

    fun scollega(chi: Activity? = null) {
        // Non staccare il player nuovo quando si chiude un'istanza vecchia.
        if (chi != null && activity !== chi) return
        activity = null
        webView = null
    }

    fun gestisci(richiestaJson: String): String {
        val id = idRichiesta(richiestaJson)
        val metodo = metodoRichiesta(richiestaJson)
        val porta = portaRichiesta(richiestaJson)
        try {
            val risposta =
                when (porta) {
                    "piattaforma" -> gestisciPiattaforma(id, metodo, richiestaJson)
                    "cache" -> gestisciCache(id, metodo, richiestaJson)
                    else -> errore(id, porta, metodo, "non_supportato", "porta sconosciuta")
                }
            if (risposta is JsonObject) return codificaRisposta(risposta)
            return codificaRispostaDaOggetto(risposta)
        } catch (e: UnsupportedOperationException) {
            return codificaRispostaDaOggetto(
                errore(id, porta, metodo, "non_supportato", e.message ?: "non supportato"),
            )
        } catch (e: SecurityException) {
            return codificaRispostaDaOggetto(
                errore(id, porta, metodo, "rifiutato", e.message ?: "rifiutato"),
            )
        } catch (e: Exception) {
            return codificaRispostaDaOggetto(
                errore(id, porta, metodo, "interno", e.message ?: "errore interno"),
            )
        }
    }

    private fun gestisciPiattaforma(id: String, metodo: String, json: String): Any =
        when (metodo) {
            "info" -> {
                ponteJson.decodeFromString(RichiestaInfo.serializer(), json)
                RispostaInfo(
                    id = id,
                    risultato =
                        RisultatoInfo(
                            piattaforma = "android",
                            modello = Build.MODEL ?: "Android",
                            versionePlayer = BuildConfig.VERSIONE_PLAYER,
                        ),
                )
            }
            "capacita" -> {
                ponteJson.decodeFromString(RichiestaCapacita.serializer(), json)
                val capacita = mutableListOf("istantanea", "ricarica", "riavvia_app", "invia_log", "svuota_cache", "aggiorna_player", "luminosita", "assistenza", "fine_assistenza")
                if (puoRiavviareDispositivo()) capacita.add("riavvia_dispositivo")
                RispostaCapacita(id = id, risultato = RisultatoCapacita(capacita = capacita))
            }
            "istantanea" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaIstantanea.serializer(), json)
                val act = activity ?: throw IllegalStateException("activity non collegata")
                val wv = webView ?: throw IllegalStateException("webView non collegata")
                val larghezzaMax =
                    runCatching { richiesta.parametri["larghezzaMax"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0
                val (mime, base64) = IstantaneaSchermo.cattura(act, wv, larghezzaMax)
                RispostaIstantanea(id = id, risultato = RisultatoIstantanea(mime = mime, base64 = base64))
            }
            "riavviaApp" -> {
                ponteJson.decodeFromString(RichiestaRiavviaApp.serializer(), json)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        RiavvioActivity.riavvia(contesto)
                    } catch (_: Exception) {
                        val intent =
                            Intent(contesto, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        contesto.startActivity(intent)
                    }
                }, 500)
                RispostaRiavviaApp(id = id, risultato = RisultatoAccettato())
            }
            "riavviaDispositivo" -> {
                ponteJson.decodeFromString(RichiestaRiavviaDispositivo.serializer(), json)
                // Riavvio con il componente amministratore vero (prima veniva passato null e Android lo rifiutava).
                if (!puoRiavviareDispositivo() || !GestoreDispositivo.riavvia(contesto)) {
                    throw UnsupportedOperationException("riavvio dispositivo non disponibile")
                }
                RispostaRiavviaDispositivo(id = id, risultato = RisultatoAccettato())
            }
            "archivioSicuro.leggi" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaArchivioLeggi.serializer(), json)
                val (presente, valore) = archivio.leggi(richiesta.parametri.chiave)
                RispostaArchivioLeggi(
                    id = id,
                    risultato = RisultatoArchivioLeggi(presente = presente, valore = valore),
                )
            }
            "archivioSicuro.scrivi" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaArchivioScrivi.serializer(), json)
                archivio.scrivi(richiesta.parametri.chiave, richiesta.parametri.valore)
                RispostaArchivioScrivi(id = id, risultato = RisultatoScritto())
            }
            "archivioSicuro.cancella" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaArchivioCancella.serializer(), json)
                archivio.cancella(richiesta.parametri.chiave)
                RispostaArchivioCancella(id = id, risultato = RisultatoCancellato())
            }
            "stato" -> risposta(id, "stato", aJson(stato()))
            "aggiornaApp" -> {
                val parametri = runCatching { ponteJson.parseToJsonElement(json).jsonObject["parametri"]?.jsonObject }.getOrNull()
                val forzato =
                    runCatching { parametri?.get("forzato")?.jsonPrimitive?.content != "false" }.getOrDefault(true)
                // 1.0.15: il player web può inoltrare l'istruzione del server (benvenuto/comando), stesso formato
                // di GET /api/v1/player/aggiornamento. Senza, il guscio la legge da sé.
                val istruzione = runCatching {
                    parametri?.get("istruzione")?.jsonObject?.let { o ->
                        val base = net.spotlive.player.aggiornamenti.AggiornatoreApk.origine() + "/"
                        net.spotlive.player.aggiornamenti.RegolaAggiornamento.leggi(o, base, "ponte")
                    }
                }.getOrNull()
                risposta(id, "aggiornaApp", aJson(AggiornatoreApk.avvia(contesto, forzato, istruzione)))
            }
            // 1.0.15: luminosità del LED (Taurus, volatile). {pct: 5..100} → {esito, lettaPct | codice}.
            // Chi chiama questo metodo diventa l'applicatore: quello nativo si ferma per 5 minuti.
            "luminosita" -> {
                val pct = runCatching {
                    ponteJson.parseToJsonElement(json).jsonObject["parametri"]?.jsonObject?.get("pct")?.jsonPrimitive?.intOrNull
                }.getOrNull() ?: throw IllegalArgumentException("pct mancante")
                if (pct !in 5..100) throw IllegalArgumentException("pct fuori intervallo 5..100")
                val app = contesto.applicationContext as net.spotlive.player.PlayerApplicazione
                app.luminosita.segnaChiamataWeb()
                risposta(id, "luminosita", aJson(app.luminosita.impostaOra(pct)))
            }
            "luminositaStato" -> {
                val app = contesto.applicationContext as net.spotlive.player.PlayerApplicazione
                risposta(id, "luminositaStato", aJson(app.luminosita.stato()))
            }
            // Modalità assistenza a tempo: {minuti: 30|60}. È la stessa pausa manutenzione del watchdog:
            // il player va dietro per il tecnico e a scadenza il watchdog lo riapre da solo (~20 s).
            // Niente finish(), watchdog e Finestrella restano attivi: servono per riprendere.
            "assistenza" -> {
                val minuti = runCatching {
                    ponteJson.parseToJsonElement(json).jsonObject["parametri"]?.jsonObject?.get("minuti")?.jsonPrimitive?.intOrNull
                }.getOrNull() ?: throw IllegalArgumentException("minuti mancante")
                if (!PausaAssistenza.durataValida(minuti)) throw IllegalArgumentException("minuti ammessi: 30 o 60")
                Configurazione.pausa(contesto, minuti * 60_000L)
                Diario.evento(contesto, "assistenza da remoto: pausa di $minuti minuti, player dietro")
                activity?.let { act ->
                    act.runOnUiThread {
                        val am = act.getSystemService(ActivityManager::class.java)
                        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) runCatching { act.stopLockTask() }
                        runCatching { act.moveTaskToBack(true) }
                    }
                }
                risposta(
                    id,
                    "assistenza",
                    aJson(mapOf("accettato" to true, "minuti" to minuti, "pausaManutenzioneSec" to Configurazione.pausaRestanteMs(contesto) / 1000)),
                )
            }
            "fineAssistenza" -> {
                Configurazione.finePausa(contesto)
                Diario.evento(contesto, "fine assistenza da remoto: riporto il player davanti")
                // Se Android blocca l'avvio da sottofondo, a pausa tolta ci riprova il watchdog (~20 s).
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching {
                        contesto.startActivity(
                            Intent(contesto, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                        )
                    }.onFailure { Diario.evento(contesto, "fine assistenza: riapertura ${it.javaClass.simpleName}") }
                }
                risposta(
                    id,
                    "fineAssistenza",
                    aJson(mapOf("accettato" to true, "pausaManutenzioneSec" to Configurazione.pausaRestanteMs(contesto) / 1000)),
                )
            }
            else -> throw UnsupportedOperationException("metodo piattaforma: $metodo")
        }

    private fun gestisciCache(id: String, metodo: String, json: String): Any =
        when (metodo) {
            "assicura" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaAssicura.serializer(), json)
                RispostaAssicura(id = id, risultato = cache.assicura(richiesta.parametri.asset))
            }
            "url" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaUrl.serializer(), json)
                RispostaUrl(id = id, risultato = cache.url(richiesta.parametri.sha256))
            }
            "libera" -> {
                val richiesta = ponteJson.decodeFromString(RichiestaLibera.serializer(), json)
                RispostaLibera(id = id, risultato = cache.libera(richiesta.parametri.daTenere))
            }
            "spazio" -> {
                ponteJson.decodeFromString(RichiestaSpazio.serializer(), json)
                RispostaSpazio(id = id, risultato = cache.spazio())
            }
            else -> throw UnsupportedOperationException("metodo cache: $metodo")
        }

    /** Memoria, batteria, rete e versione: va nel ping di telemetria (ogni 30 s). */
    private fun stato(): Map<String, Any?> {
        val am = contesto.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        val batteria = contesto.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val livello = batteria?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && sc > 0) l * 100 / sc else null
        }
        val inCarica = batteria?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 }
        val cm = contesto.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val tipoRete = when {
            caps == null -> "nessuna"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellulare"
            else -> "altra"
        }
        val nativa = android.os.Debug.getNativeHeapAllocatedSize() / 1048576
        return mapOf(
            "versioneApp" to BuildConfig.VERSION_NAME,
            "codiceApp" to BuildConfig.VERSION_CODE,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "produttore" to Build.MANUFACTURER,
            "memoriaLiberaMb" to mi.availMem / 1048576,
            "memoriaTotaleMb" to mi.totalMem / 1048576,
            "memoriaBassa" to mi.lowMemory,
            "memoriaAppMb" to (rt.totalMemory() - rt.freeMemory()) / 1048576 + nativa,
            "batteria" to livello,
            "inCarica" to inCarica,
            "rete" to tipoRete,
            "reteKbps" to caps?.linkDownstreamBandwidthKbps,
            "accesoDaMin" to SystemClock.elapsedRealtime() / 60000,
            "permessoInstallazione" to AggiornatoreApk.puoInstallare(contesto),
            "ultimoAggiornamento" to AggiornatoreApk.ultimo(contesto),
            "canaleAggiornamenti" to AggiornatoreApk.canale(contesto),
            "luminosita" to runCatching { (contesto.applicationContext as net.spotlive.player.PlayerApplicazione).luminosita.stato() }.getOrNull(),
            "configurazione" to runCatching { net.spotlive.player.sistema.Configurazione.riepilogo(contesto) }.getOrNull(),
        )
    }

    private fun aJson(v: Any?): JsonElement =
        when (v) {
            null -> JsonNull
            is JsonElement -> v
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is String -> JsonPrimitive(v)
            is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to aJson(x) })
            is List<*> -> JsonArray(v.map { aJson(it) })
            else -> JsonPrimitive(v.toString())
        }

    private fun risposta(id: String, metodo: String, risultato: JsonElement): JsonObject =
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1),
                "id" to JsonPrimitive(id),
                "ok" to JsonPrimitive(true),
                "porta" to JsonPrimitive("piattaforma"),
                "metodo" to JsonPrimitive(metodo),
                "risultato" to risultato,
            ),
        )

    private fun puoRiavviareDispositivo(): Boolean {
        val dpm = contesto.getSystemService(DevicePolicyManager::class.java)
        return dpm.isDeviceOwnerApp(contesto.packageName)
    }

    private fun errore(
        id: String,
        porta: String,
        metodo: String,
        code: String,
        message: String,
    ): RispostaErrorePonte =
        RispostaErrorePonte(
            id = id,
            porta = porta,
            metodo = metodo,
            errore = DettaglioErrorePonte(code = code, message = message),
        )
}
