package net.spotlive.player.sistema

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import net.spotlive.player.MainActivity
import net.spotlive.player.R
import net.spotlive.player.aggiornamenti.AggiornatoreApk

/**
 * Servizio in primo piano che tiene il player sempre davanti a tutto:
 * - ogni 10 s controlla che il player sia visibile; se non lo è per 2 controlli di fila lo riapre;
 * - con il permesso "Mostra sopra altre app" tiene la Finestrella invisibile di 1 px sopra le
 *   altre app: così Android considera l'app visibile e le permette di riaprire il player;
 * - senza quel permesso prova lo stesso e mostra una notifica a schermo intero "Tocca per riaprire";
 * - non interviene con schermo spento, in pausa manutenzione o durante un aggiornamento.
 */
class WatchdogServizio : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var mancati = 0

    private val controllo =
        object : Runnable {
            override fun run() {
                runCatching { controlla() }.onFailure { Log.w(TAG, "controllo", it) }
                handler.postDelayed(this, INTERVALLO_MS)
            }
        }

    /** Controllo aggiornamenti: installa da solo solo se il server lo marca "automatico". */
    private val controlloAggiornamenti =
        object : Runnable {
            override fun run() {
                Thread({
                    runCatching { AggiornatoreApk.avvia(this@WatchdogServizio, forzato = false) }
                }, "spotlive-controllo-agg").start()
                handler.postDelayed(this, INTERVALLO_AGGIORNAMENTI_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        creaCanali()
        startForeground(ID_NOTIFICA, costruisciNotifica())
        attivo = true
        Diario.evento(this, "servizio di controllo avviato")
        runCatching { Sentinella.avvia(this) }.onFailure { Diario.evento(this, "sentinella non avviata ${it.javaClass.simpleName}") }
        aggiornaFinestrella()
        GestoreDispositivo.configuraSeProprietario(this)
        handler.postDelayed(controllo, INTERVALLO_MS)
        handler.postDelayed(controlloAggiornamenti, 10 * 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** L'app è stata tolta dalle recenti: riapri subito il player (se non in manutenzione). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Diario.evento(this, "app tolta dalle recenti")
        handler.postDelayed({ if (Configurazione.pausaRestanteMs(this) == 0L && !rilancioInCorso) riportaInPrimoPiano() }, 1500)
    }

    override fun onDestroy() {
        attivo = false
        Diario.evento(this, "servizio di controllo fermato")
        handler.removeCallbacks(controllo)
        handler.removeCallbacks(controlloAggiornamenti)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var motivoSaltoPrec: String? = null
    private var spentoInOrario = 0
    private var rilancioInCorso = false

    private fun salto(motivo: String?) {
        if (motivo != motivoSaltoPrec) {
            motivoSaltoPrec = motivo
            if (motivo != null) Diario.evento(this, "controllo: non intervengo ($motivo)")
        }
    }

    private fun controlla() {
        ultimoControllo = System.currentTimeMillis()
        aggiornaFinestrella()
        if (MainActivity.visibile) {
            if (mancati > 0 || rilancioInCorso) Diario.evento(this, "player di nuovo visibile")
            mancati = 0
            spentoInOrario = 0
            salto(null)
            togliAvviso()
            return
        }
        if (Configurazione.pausaRestanteMs(this) > 0) { mancati = 0; salto("pausa manutenzione"); return }
        if (aggiornamentoInCorso()) { mancati = 0; salto("aggiornamento in corso"); return }
        if (rilancioInCorso) return
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            mancati = 0
            // Schermo spento in orario di accensione (timeout, tasto, ecc.): dopo 20 s lo riaccende.
            if (OrarioAccensione.stato(this).acceso) {
                spentoInOrario++
                if (spentoInOrario == 2) {
                    Diario.evento(this, "schermo spento in orario: lo riaccendo e riapro il player")
                    OrarioAccensione.svegliaSchermo(this)
                    spentoInOrario = 0
                }
            } else {
                salto("fuori orario, schermo spento")
            }
            return
        }
        spentoInOrario = 0
        salto(null)
        mancati++
        if (mancati == 1) Diario.evento(this, "player non visibile (1° controllo)")
        if (mancati >= 2) {
            mancati = 0
            riportaInPrimoPiano()
        }
    }

    /** Riapre il player provando, in ordine, più strade; registra nel diario quale funziona. */
    private fun riportaInPrimoPiano() {
        rilancioInCorso = true
        val tentativi = listOf<Pair<String, () -> Unit>>(
            "avvio diretto" to {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            },
            "PendingIntent consentito" to { inviaPendingIntent() },
            "intent Home" to {
                if (!Configurazione.appHome(this)) throw IllegalStateException("SpotLive non è l'app Home")
                startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        val finestra = Finestrella.riepilogo()
        Diario.evento(this, "rilancio: finestrella=${finestra["presente"]}/${finestra["visibileAndroid"]} sopraAltreApp=${Configurazione.sopraAltreApp(this)}")
        fun prova(i: Int) {
            if (MainActivity.visibile) {
                val metodo = tentativi[maxOf(0, i - 1)].first
                ultimoRilancio = "riuscito: $metodo"
                Diario.evento(this, "rilancio riuscito con «$metodo»")
                rilancioInCorso = false
                return
            }
            if (i >= tentativi.size) {
                ultimoRilancio = "fallito"
                Diario.evento(this, "rilancio FALLITO con tutti i metodi: avviso a schermo intero")
                mostraAvviso()
                rilancioInCorso = false
                return
            }
            val (nome, azione) = tentativi[i]
            runCatching { azione() }
                .onSuccess { Diario.evento(this, "rilancio: provo «$nome»") }
                .onFailure { Diario.evento(this, "rilancio: «$nome» errore ${it.javaClass.simpleName}: ${it.message}") }
            handler.postDelayed({ prova(i + 1) }, 3000)
        }
        prova(0)
    }

    private fun inviaPendingIntent() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (Build.VERSION.SDK_INT >= 34) {
            val creatore = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            val pi = PendingIntent.getActivity(
                this, 7401, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, creatore.toBundle(),
            )
            val mittente = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(this, 0, null, null, null, null, mittente.toBundle())
        } else {
            PendingIntent.getActivity(this, 7401, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).send()
        }
    }

    /** Non coprire la finestra "Aggiorna"/"Installa comunque" mentre si installa una versione. */
    private fun aggiornamentoInCorso(): Boolean {
        val u = AggiornatoreApk.ultimo(this) ?: return false
        val quando = (u["quando"] as? Long) ?: return false
        val trascorso = System.currentTimeMillis() - quando
        return when (u["esito"]) {
            "download", "installazione" -> trascorso < 5 * 60_000L
            "attesa_conferma", "permesso_richiesto" -> trascorso < 10 * 60_000L
            else -> false
        }
    }

    private fun aggiornaFinestrella() = Finestrella.aggiorna(this)

    private fun creaCanali() {
        val nm = getSystemService(NotificationManager::class.java)
        val fisso =
            NotificationChannel(CANALE_ID, getString(R.string.canale_watchdog_nome), NotificationManager.IMPORTANCE_MIN)
        fisso.description = getString(R.string.canale_watchdog_descrizione)
        fisso.setShowBadge(false)
        nm.createNotificationChannel(fisso)
        val avviso = NotificationChannel(CANALE_AVVISO, "Riapertura player", NotificationManager.IMPORTANCE_HIGH)
        avviso.setShowBadge(false)
        nm.createNotificationChannel(avviso)
    }

    private fun intentPlayer(codice: Int) =
        PendingIntent.getActivity(
            this,
            codice,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun costruisciNotifica(): Notification =
        NotificationCompat.Builder(this, CANALE_ID)
            .setContentTitle(getString(R.string.nome_app))
            .setContentText(getString(R.string.canale_watchdog_descrizione))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(intentPlayer(0))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    private fun mostraAvviso() {
        val n = NotificationCompat.Builder(this, CANALE_AVVISO)
            .setContentTitle("SpotLive Player")
            .setContentText("Tocca per riaprire il player")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(intentPlayer(1))
            .setFullScreenIntent(intentPlayer(2), true)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(ID_AVVISO, n) }
    }

    private fun togliAvviso() {
        runCatching { getSystemService(NotificationManager::class.java).cancel(ID_AVVISO) }
    }

    companion object {
        @Volatile var attivo = false
        @Volatile var ultimoControllo = 0L
        @Volatile var ultimoRilancio: String? = null

        fun riepilogo(): Map<String, Any?> = mapOf(
            "servizioAttivo" to attivo,
            "ultimoControlloSecFa" to if (ultimoControllo == 0L) null else (System.currentTimeMillis() - ultimoControllo) / 1000,
            "ultimoRilancio" to ultimoRilancio,
            "finestrella" to Finestrella.riepilogo(),
            "playerVisibile" to MainActivity.visibile,
            "sentinella" to Sentinella.riepilogo(),
        )

        private const val TAG = "SpotLiveWatchdog"
        private const val CANALE_ID = "spotlive_watchdog"
        private const val CANALE_AVVISO = "spotlive_riapertura"
        private const val ID_NOTIFICA = 1001
        private const val ID_AVVISO = 1002
        private const val INTERVALLO_MS = 10_000L
        private const val INTERVALLO_AGGIORNAMENTI_MS = 6 * 60 * 60_000L
    }
}
