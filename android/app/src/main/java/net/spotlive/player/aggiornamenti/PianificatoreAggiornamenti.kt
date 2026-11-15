package net.spotlive.player.aggiornamenti

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Controllo aggiornamenti senza comando (1.0.15): primo giro tra 3 e 10 minuti dall'avvio, poi ogni ora con
 * qualche minuto di scarto, così 1000+ player non chiedono tutti nello stesso secondo.
 * Installa solo ciò che il server segna per il canale del dispositivo (o "automatico" nel file storico).
 */
object PianificatoreAggiornamenti {
    private var avviato = false

    @Synchronized
    fun avvia(contesto: Context) {
        if (avviato) return
        avviato = true
        val app = contesto.applicationContext
        val es = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "spotlive-controllo-aggiornamenti").apply { isDaemon = true } }
        val primo = 180L + Random.nextLong(420)
        es.scheduleWithFixedDelay({ AggiornatoreApk.controllaInSottofondo(app) }, primo, 3600L + Random.nextLong(600), TimeUnit.SECONDS)
    }
}
