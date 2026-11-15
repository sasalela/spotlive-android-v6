package net.spotlive.player.aggiornamenti

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.spotlive.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aggiornamento dell'app da remoto, senza root (1.0.15: canale per dispositivo, firma verificata, installazione
 * silenziosa dove possibile, avviso per il tecnico altrove).
 *
 * Fonti, in ordine (la prima che risponde decide):
 *  1. server, per questo dispositivo: GET /api/v1/player/aggiornamento?piattaforma=android&versionCode=N con il token
 *     del dispositivo → istruzione del suo canale (prova/stabile) oppure 204 = niente da fare;
 *  2. file del canale: /aggiornamenti/android/{canale}/versione-android.json (canale = ultimo ricevuto, sennò stabile);
 *  3. file storico /scarica-…/versione-android.json (installa solo se "automatico": true o su comando).
 * Controlli prima di installare: SHA-256 dichiarato, pacchetto net.spotlive.player, firma uguale all'app installata
 * e al certificato SpotLive, versionCode più alto.
 * Installazione: proprietario del dispositivo o Android 12+ con SpotLive installatore → nessun tocco;
 * altrimenti si aspetta il comando del tecnico ("Aggiorna player") e Android mostra «Aggiorna».
 */
object AggiornatoreApk {
    private const val TAG = "SpotLiveAggiorna"
    private const val PREF = "spotlive_aggiornamento"
    const val AZIONE_ESITO = "net.spotlive.player.ESITO_INSTALLAZIONE"
    private const val PERCORSO_STORICO = "/scarica-QkQs5b4iSpzd4BSW66qHFxD8/versione-android.json"
    private val inCorso = AtomicBoolean(false)
    private val json = Json { ignoreUnknownKeys = true }

    // SVILUPPO-20261114-02: fiducia TLS della variante (standard = identità).
    private val client =
        net.spotlive.player.piattaforma.Tls.applica(
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS),
        ).build()

    /** SVILUPPO-20261114-02: la legacy legge solo il proprio file statico (mai endpoint per dispositivo né storico). */
    const val PERCORSO_LEGACY = "/aggiornamenti/android/legacy/versione-android.json"

    fun origine(): String? {
        val u = Uri.parse(BuildConfig.URL_PLAYER)
        if (u.scheme != "https" || u.authority.isNullOrEmpty()) return null
        return "https://${u.authority}"
    }

    fun urlVersione(): String? = origine()?.let { it + PERCORSO_STORICO }

    fun canale(contesto: Context): String =
        contesto.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("canale", "stabile") ?: "stabile"

    private fun token(contesto: Context): String? = runCatching {
        (contesto.applicationContext as net.spotlive.player.PlayerApplicazione).archivioSicuro
            .leggi(net.spotlive.player.piattaforma.ArchivioSicuro.CHIAVE_TOKEN).second
    }.getOrNull()

    /** null = il server dice che non c'è niente da fare per questo dispositivo. */
    fun leggiIstruzione(contesto: Context): IstruzioneAndroid? {
        val org = origine() ?: throw IllegalStateException("indirizzo aggiornamenti assente")
        if (BuildConfig.VARIANTE == "legacy") {
            return leggiFile("$org$PERCORSO_LEGACY", "canale", "legacy")?.copy(canale = "legacy")
                ?: throw IllegalStateException("versione legacy non leggibile")
        }
        val tok = token(contesto)
        if (tok != null) {
            val url = "$org/api/v1/player/aggiornamento?piattaforma=android&versionCode=${BuildConfig.VERSION_CODE}"
            val r = runCatching {
                client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $tok").build()).execute()
            }.getOrNull()
            r?.use {
                if (it.code == 204) return null
                if (it.isSuccessful) {
                    val o = json.parseToJsonElement(it.body?.string() ?: "{}").jsonObject
                    val i = RegolaAggiornamento.leggi(o, url, "server", canale(contesto))
                        ?: throw IllegalStateException("istruzione del server non valida")
                    salvaCanale(contesto, i.canale)
                    return i
                }
                // 404/405: server senza l'endpoint (versione vecchia) → file statici.
            }
        }
        val can = canale(contesto)
        leggiFile("$org/aggiornamenti/android/$can/versione-android.json", "canale", can)?.let { return it }
        return leggiFile("$org$PERCORSO_STORICO", "storico", "stabile")
            ?: throw IllegalStateException("versione non leggibile")
    }

    private fun leggiFile(url: String, fonte: String, canale: String): IstruzioneAndroid? {
        val richiesta = Request.Builder().url("$url?t=${System.currentTimeMillis()}").build()
        val risposta = runCatching { client.newCall(richiesta).execute() }.getOrNull() ?: return null
        risposta.use { r ->
            if (!r.isSuccessful) return null
            // Una pagina HTML (ripiego del pannello) o un JSON diverso valgono "file assente".
            val o = runCatching { json.parseToJsonElement(r.body?.string() ?: "{}").jsonObject }.getOrNull() ?: return null
            return RegolaAggiornamento.leggi(o, url, fonte, canale)
        }
    }

    private fun salvaCanale(contesto: Context, canale: String) {
        contesto.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("canale", canale).apply()
    }

    fun puoInstallare(contesto: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || contesto.packageManager.canRequestPackageInstalls()

    /** Installazione senza tocchi: proprietario del dispositivo, oppure Android 12+ e SpotLive ha installato sé stessa. */
    fun silenzioso(contesto: Context): Boolean {
        // SVILUPPO-20261114-02: installazione silenziosa da proprietario non verificata sotto API 23 → mai silenziosa.
        if (net.spotlive.player.sistema.GestoreDispositivo.proprietario(contesto)) return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            contesto.packageManager.getInstallSourceInfo(contesto.packageName).installingPackageName == contesto.packageName
        }.getOrDefault(false)
    }

    /**
     * Controlla e, se serve, avvia download e installazione in sottofondo.
     * Esiti: non_necessario | non_automatico | ritorno_non_possibile | attendi_fuori_orario | attesa_tecnico |
     *        avviato | in_corso | permesso_richiesto.
     * @param istruzione facoltativa, già ricevuta dal player web (benvenuto/comando): evita una lettura.
     */
    fun avvia(contesto: Context, forzato: Boolean, istruzione: IstruzioneAndroid? = null): Map<String, Any?> {
        val app = contesto.applicationContext
        val pub = istruzione ?: leggiIstruzione(app)
        val base = mutableMapOf<String, Any?>(
            "versioneAttuale" to BuildConfig.VERSION_NAME,
            "codiceAttuale" to BuildConfig.VERSION_CODE,
            "canale" to (pub?.canale ?: canale(app)),
            "silenzioso" to silenzioso(app),
        )
        if (pub == null) return base + ("esito" to "non_necessario")
        base += mapOf("versionePubblicata" to pub.versionName, "codicePubblicato" to pub.versionCode, "fonte" to pub.fonte)
        // SVILUPPO-20261114-02: mai un APK "-legacy" su una standard né viceversa (vale anche per l'istruzione da ponte).
        if (!RegolaAggiornamento.varianteCompatibile(pub.versionName, BuildConfig.VARIANTE)) {
            registra(app, "variante_diversa", "${pub.versionName} (${pub.versionCode}) non è per la variante ${BuildConfig.VARIANTE} (fonte ${pub.fonte})")
            return base + ("esito" to "variante_diversa")
        }
        val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val chiaveAttesa = "attesa_${pub.versionCode}"
        val daQuando = p.getLong(chiaveAttesa, 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { p.edit().putLong(chiaveAttesa, it).apply() }
        val orario = runCatching { net.spotlive.player.sistema.OrarioAccensione.stato(app) }.getOrNull()
        val decisione = RegolaAggiornamento.decidi(
            pub, BuildConfig.VERSION_CODE, forzato, silenzioso(app),
            orarioNoto = orario?.noto == true, accesoOra = orario?.acceso == true,
            inAttesaDaMs = System.currentTimeMillis() - daQuando,
            oraLocale = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
        )
        when (decisione) {
            Decisione.NON_NECESSARIO -> return base + ("esito" to "non_necessario")
            Decisione.NON_AUTOMATICO -> return base + ("esito" to "non_automatico")
            Decisione.RITORNO_NON_POSSIBILE -> {
                registra(app, "ritorno_non_possibile", "il server indica ${pub.versionName} (${pub.versionCode}), installata ${BuildConfig.VERSION_CODE}: serve ripubblicarla con versionCode più alto")
                return base + ("esito" to "ritorno_non_possibile")
            }
            Decisione.ATTENDI_FUORI_ORARIO -> return base + ("esito" to "attendi_fuori_orario")
            Decisione.ATTESA_TECNICO -> {
                registra(app, "attesa_tecnico", "${pub.versionName} pronta: serve «Aggiorna player» dal pannello con un tecnico sul posto")
                return base + ("esito" to "attesa_tecnico")
            }
            Decisione.INSTALLA -> Unit
        }
        if (!puoInstallare(app)) {
            registra(app, "permesso_richiesto", "consentire 'Installa app sconosciute' per SpotLive")
            if (forzato && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(i) }
            }
            return base + ("esito" to "permesso_richiesto")
        }
        if (!inCorso.compareAndSet(false, true)) return base + ("esito" to "in_corso")
        Thread({
            try {
                scaricaEInstalla(app, pub)
            } catch (e: Exception) {
                Log.w(TAG, "aggiornamento fallito", e)
                registra(app, "fallito", e.message ?: e.javaClass.simpleName)
            } finally {
                inCorso.set(false)
            }
        }, "spotlive-aggiorna").start()
        return base + ("esito" to "avviato")
    }

    /** Controllo periodico (senza comando): rispetta "automatico", il canale e l'orario. Mai eccezioni. */
    fun controllaInSottofondo(contesto: Context) {
        runCatching { avvia(contesto, forzato = false) }
            .onFailure { Log.i(TAG, "controllo aggiornamenti: ${it.message}") }
    }

    private fun scaricaEInstalla(contesto: Context, pub: IstruzioneAndroid) {
        registra(contesto, "download", pub.versionName)
        val file = File(contesto.cacheDir, "aggiornamento.apk")
        val md = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(pub.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("download APK: HTTP ${r.code}")
            val corpo = r.body ?: throw IllegalStateException("APK vuoto")
            file.outputStream().use { out ->
                corpo.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (sha != pub.sha256) {
            file.delete()
            throw IllegalStateException("APK corrotto (sha256 diverso)")
        }
        val (pacchetto, codice, certApk, nomeVersione) = FirmaApk.archivio(contesto, file)
        if (!RegolaAggiornamento.firmaValida(pacchetto, certApk, FirmaApk.installata(contesto), pub.certSha256)) {
            file.delete()
            throw IllegalStateException("APK rifiutato: firma o pacchetto non SpotLive")
        }
        if (codice != pub.versionCode.toLong()) {
            file.delete()
            throw IllegalStateException("APK rifiutato: versionCode $codice diverso da ${pub.versionCode}")
        }
        // SVILUPPO-20261114-02: la variante si controlla sul versionName reale dell'APK, non su quello dichiarato.
        if (!RegolaAggiornamento.varianteCompatibile(nomeVersione ?: "", BuildConfig.VARIANTE)) {
            file.delete()
            throw IllegalStateException("APK rifiutato: versionName $nomeVersione non è per la variante ${BuildConfig.VARIANTE}")
        }
        val installer = contesto.packageManager.packageInstaller
        val parametri = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        parametri.setAppPackageName(contesto.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            parametri.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val idSessione = installer.createSession(parametri)
        installer.openSession(idSessione).use { sessione ->
            sessione.openWrite("spotlive.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                sessione.fsync(out)
            }
            val esito = Intent(contesto, EsitoInstallazioneRicevitore::class.java).setAction(AZIONE_ESITO)
            val flag =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(contesto, idSessione, esito, flag)
            registra(contesto, "installazione", pub.versionName)
            sessione.commit(pi.intentSender)
        }
        file.delete()
    }

    fun registra(contesto: Context, esito: String, dettaglio: String) {
        val p = contesto.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val uguale = p.getString("esito", null) == esito && p.getString("dettaglio", null) == dettaglio.take(200)
        if (uguale && esito in setOf("attesa_tecnico", "ritorno_non_possibile", "permesso_richiesto", "variante_diversa")) return
        p.edit()
            .putString("esito", esito)
            .putString("dettaglio", dettaglio.take(200))
            .putLong("quando", System.currentTimeMillis())
            .apply()
        runCatching { net.spotlive.player.sistema.Diario.evento(contesto, "aggiornamento: $esito ${dettaglio.take(120)}") }
    }

    fun ultimo(contesto: Context): Map<String, Any?>? {
        val p = contesto.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val esito = p.getString("esito", null) ?: return null
        return mapOf(
            "esito" to esito,
            "dettaglio" to p.getString("dettaglio", ""),
            "quando" to p.getLong("quando", 0),
            "canale" to canale(contesto),
            "silenzioso" to silenzioso(contesto),
        )
    }
}

/** SVILUPPO-20261114-02: dati dell'APK scaricato (prima una Triple, ora anche il versionName). */
data class InfoApk(val pacchetto: String?, val codice: Long, val certificati: Set<String>, val versionName: String?)

/** Certificati di firma (SHA-256 esadecimale) dell'APK scaricato e dell'app installata. */
object FirmaApk {
    @Suppress("DEPRECATION")
    fun archivio(contesto: Context, file: File): InfoApk {
        val pm = contesto.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(file.absolutePath, flag) ?: return InfoApk(null, -1, emptySet(), null)
        val codice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return InfoApk(info.packageName, codice, impronte(info), info.versionName)
    }

    @Suppress("DEPRECATION")
    fun installata(contesto: Context): Set<String> {
        val pm = contesto.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return impronte(pm.getPackageInfo(contesto.packageName, flag))
    }

    @Suppress("DEPRECATION")
    private fun impronte(info: PackageInfo): Set<String> {
        val firme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { si -> if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory }
                ?: info.signatures
        } else {
            info.signatures
        }
        return firme.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}

/** Riceve l'esito di PackageInstaller: se serve la conferma apre la finestra di Android. */
class EsitoInstallazioneRicevitore : BroadcastReceiver() {
    override fun onReceive(contesto: Context, intent: Intent) {
        val stato = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val messaggio = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        when (stato) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                AggiornatoreApk.registra(contesto, "attesa_conferma", "tocca 'Aggiorna' sul tablet")
                @Suppress("DEPRECATION")
                val conferma = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (conferma != null) {
                    conferma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { contesto.startActivity(conferma) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> AggiornatoreApk.registra(contesto, "installato", messaggio)
            else -> AggiornatoreApk.registra(contesto, "fallito", "stato $stato $messaggio")
        }
    }
}
