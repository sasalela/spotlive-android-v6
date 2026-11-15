package net.spotlive.player.sistema

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import net.spotlive.player.MainActivity
import net.spotlive.player.aggiornamenti.AggiornatoreApk

/**
 * Riapre il player all'accensione (anche dopo un'interruzione di corrente), all'avvio rapido
 * di alcuni produttori e subito dopo un aggiornamento dell'app.
 * Il ricevitore NON è "direct boot aware": parte appena il tablet è sbloccato. Con blocco
 * schermo "Nessuno" (consigliato per i player) succede subito dopo l'accensione.
 */
class AvvioRicevitore : BroadcastReceiver() {
    override fun onReceive(contesto: Context, intent: Intent?) {
        val azione = intent?.action ?: return
        if (azione !in AZIONI) return
        Log.i("SpotLiveAvvio", "avvio da $azione")
        Diario.evento(contesto, "avvio da ${azione.substringAfterLast('.')} (sopraAltreApp=${Configurazione.sopraAltreApp(contesto)}, home=${Configurazione.appHome(contesto)})")
        if (azione == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AggiornatoreApk.registra(contesto, "installato", "aggiornata")
        }
        // Un riavvio annulla l'eventuale pausa manutenzione rimasta.
        if (azione != Intent.ACTION_MY_PACKAGE_REPLACED) Configurazione.finePausa(contesto)
        val app = contesto.applicationContext
        Finestrella.aggiorna(app)
        OrarioAccensione.programma(app)
        val servizio = {
            // SVILUPPO-20261114-02: da API 26 chiama startForegroundService come prima, sotto startService.
            runCatching { androidx.core.content.ContextCompat.startForegroundService(app, Intent(app, WatchdogServizio::class.java)) }
                .onFailure { Diario.evento(app, "avvio: servizio non avviato ${it.javaClass.simpleName}") }
        }
        servizio()
        val apri = {
            runCatching {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    ),
                )
            }
        }
        apri()
        // Altri due tentativi (sistemi lenti all'accensione). Il ricevitore termina subito (niente
        // goAsync: su un tablet lento rischierebbe un "app non risponde"); il processo resta vivo
        // grazie al servizio in primo piano, che comunque ricontrolla ogni 10 s.
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ if (!MainActivity.visibile) { servizio(); apri() } }, 3000)
        h.postDelayed({ if (!MainActivity.visibile) { servizio(); apri() } }, 10000)
    }

    companion object {
        private val AZIONI = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
