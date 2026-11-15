package net.spotlive.player.sistema

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import net.spotlive.player.MainActivity

/**
 * Sentinella nativa (dalla 1.0.5): scrive nel diario il motivo dell'avvio, lo schermo che si spegne o si
 * riaccende, batteria e carica, la rete che cade e torna; prova a ricollegare il Wi-Fi dopo
 * l'accensione o una caduta della rete e, al ritorno, avvisa il player perché si riallinei subito.
 * Limite: da Android 10 un'app normale non può accendere il Wi-Fi se è spento (solo il
 * proprietario del dispositivo); può solo chiedere al sistema di ricollegarsi alle reti salvate.
 */
object Sentinella {
    private const val PREF = "spotlive_sentinella"
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var avviata = false
    @Volatile private var reteOk: Boolean? = null
    @Volatile private var persaDal = 0L
    @Volatile private var tentativi = 0
    @Volatile private var ultimoLivello = -1
    @Volatile private var inCarica: Boolean? = null
    @Volatile var motivoAvvio: String? = null
        private set
    private var richiestaWifi: ConnectivityManager.NetworkCallback? = null

    fun avvia(c0: Context) {
        if (avviata) return
        avviata = true
        val c = c0.applicationContext
        registraMotivoAvvio(c)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent?) { runCatching { evento(c, i ?: return) } }
        }
        runCatching { androidx.core.content.ContextCompat.registerReceiver(c, r, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
        runCatching {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (ok) reteTornata(c, if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "altra rete")
                }
                override fun onLost(n: Network) { retePersa(c) }
            })
        }
        // Dopo l'accensione: se entro 60 s non c'è rete valida, prova a ricollegare.
        h.postDelayed({ if (reteOk != true) { if (persaDal == 0L) persaDal = SystemClock.elapsedRealtime(); Diario.evento(c, "rete: nessuna connessione 60 s dopo l'avvio"); ricollega(c) } }, 60_000L)
    }

    private fun livello(i: Intent): Int {
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) -1 else l * 100 / s
    }

    private fun evento(c: Context, i: Intent) {
        val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        when (i.action) {
            Intent.ACTION_SCREEN_OFF -> Diario.evento(c, "schermo spento (${if (OrarioAccensione.stato(c).acceso) "in orario: lo riaccendo tra 20 s" else "fuori orario"}; batteria ${ultimoLivello}%)")
            Intent.ACTION_SCREEN_ON -> Diario.evento(c, "schermo acceso")
            Intent.ACTION_POWER_CONNECTED -> Diario.evento(c, "alimentatore collegato (batteria ${ultimoLivello}%)")
            Intent.ACTION_POWER_DISCONNECTED -> Diario.evento(c, "alimentatore SCOLLEGATO (batteria ${ultimoLivello}%)")
            Intent.ACTION_BATTERY_LOW -> Diario.evento(c, "batteria scarica (${ultimoLivello}%)")
            Intent.ACTION_BATTERY_OKAY -> Diario.evento(c, "batteria di nuovo sufficiente (${ultimoLivello}%)")
            Intent.ACTION_SHUTDOWN -> {
                Diario.evento(c, "spegnimento o riavvio ordinato in corso (batteria ${ultimoLivello}%)")
                p.edit().putLong("spegnimento", System.currentTimeMillis()).commit()
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val liv = livello(i)
                val stato = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
                if (inCarica == null || stato != inCarica || (liv >= 0 && ultimoLivello >= 0 && liv / 10 != ultimoLivello / 10)) {
                    Diario.evento(c, "batteria $liv% ${if (stato) "in carica" else "NON in carica"}, $temp °C")
                }
                inCarica = stato
                ultimoLivello = liv
                p.edit().putInt("livello", liv).putBoolean("carica", stato).apply()
            }
        }
    }

    private fun registraMotivoAvvio(c: Context) {
        val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val avvioTablet = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val noto = p.getLong("avvioTablet", 0L)
        val uscita = ultimaUscitaApp(c)
        if (kotlin.math.abs(avvioTablet - noto) > 120_000L) {
            // Nuova accensione del tablet.
            val spento = p.getLong("spegnimento", 0L)
            val liv = p.getInt("livello", -1)
            val carica = p.getBoolean("carica", true)
            val motivo = when {
                spento in (noto + 1)..avvioTablet -> "riavvio o spegnimento ordinato (tasto, aggiornamento di sistema o riavvio da remoto)"
                noto == 0L -> "primo avvio dopo l'installazione della ${net.spotlive.player.BuildConfig.VERSION_NAME} (nessuna accensione precedente registrata)"
                liv in 0..5 && !carica -> "spegnimento improvviso: batteria esaurita (ultimo valore $liv%, non in carica)"
                else -> "spegnimento improvviso senza preavviso (mancanza di corrente con batteria guasta, blocco o riavvio forzato; ultimo valore batteria $liv%${if (carica) ", in carica" else ""})"
            }
            motivoAvvio = motivo
            Diario.evento(c, "ACCENSIONE tablet: $motivo")
            p.edit().putLong("avvioTablet", avvioTablet).apply()
        } else {
            motivoAvvio = "riavvio dell'app senza riavvio del tablet${if (uscita != null) ": $uscita" else ""}"
            Diario.evento(c, "app ripartita: ${uscita ?: "motivo non disponibile"}")
        }
    }

    private fun ultimaUscitaApp(c: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            val am = c.getSystemService(ActivityManager::class.java)
            val e = am.getHistoricalProcessExitReasons(c.packageName, 0, 1).firstOrNull() ?: return null
            val r = when (e.reason) {
                ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "errore dell'app"
                ApplicationExitInfo.REASON_ANR -> "app bloccata (non risponde)"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "memoria esaurita"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "chiusa dall'utente o reinstallata"
                ApplicationExitInfo.REASON_USER_STOPPED -> "arresto forzato"
                ApplicationExitInfo.REASON_SIGNALED -> "chiusa dal sistema"
                ApplicationExitInfo.REASON_EXIT_SELF -> "chiusa da sé (riavvio app)"
                ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permesso cambiato"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "componente di sistema fermato"
                else -> "codice ${e.reason}"
            }
            "ultima chiusura: $r"
        }.getOrNull()
    }

    private fun retePersa(c: Context) {
        if (reteOk == false) return
        reteOk = false
        persaDal = SystemClock.elapsedRealtime()
        tentativi = 0
        Diario.evento(c, "rete PERSA")
        h.postDelayed({ if (reteOk == false) ricollega(c) }, 30_000L)
    }

    private fun reteTornata(c: Context, tipo: String) {
        if (reteOk == true) return
        val eraPersa = reteOk == false || persaDal > 0L
        reteOk = true
        val durata = if (persaDal > 0L) (SystemClock.elapsedRealtime() - persaDal) / 1000 else 0
        persaDal = 0L
        tentativi = 0
        rilasciaRichiesta(c)
        Diario.evento(c, if (eraPersa) "rete tornata ($tipo) dopo ${durata} s" else "rete disponibile ($tipo)")
        if (eraPersa) h.postDelayed({ MainActivity.reteTornata(durata) }, 3000)
    }

    /** Chiede al sistema di ricollegarsi; ritenta ogni 2 minuti finché la rete non torna. */
    private fun ricollega(c: Context) {
        if (reteOk == true) return
        tentativi++
        val wm = c.getSystemService(WifiManager::class.java)
        val acceso = runCatching { wm.isWifiEnabled }.getOrDefault(true)
        if (!acceso) {
            val ok = GestoreDispositivo.proprietario(c) && runCatching { @Suppress("DEPRECATION") wm.setWifiEnabled(true) }.getOrDefault(false)
            Diario.evento(c, if (ok) "rete: Wi-Fi spento, riacceso (proprietario dispositivo)" else "rete: Wi-Fi SPENTO sul tablet: va riacceso a mano (Android non lo permette alle app)")
        } else {
            @Suppress("DEPRECATION") val r = runCatching { wm.reconnect() }.getOrDefault(false)
            if (richiestaWifi == null) runCatching {
                val cb = object : ConnectivityManager.NetworkCallback() {}
                c.getSystemService(ConnectivityManager::class.java).requestNetwork(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb,
                )
                richiestaWifi = cb
            }
            Diario.evento(c, "rete: tentativo di ricollegamento Wi-Fi n.$tentativi (reconnect=${r})")
        }
        if (tentativi < 30) h.postDelayed({ if (reteOk != true) ricollega(c) }, 120_000L)
    }

    private fun rilasciaRichiesta(c: Context) {
        val cb = richiestaWifi ?: return
        richiestaWifi = null
        runCatching { c.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
    }

    fun riepilogo(): Map<String, Any?> = mapOf(
        "motivoAvvio" to motivoAvvio,
        "rete" to when (reteOk) { true -> "ok"; false -> "persa"; null -> "sconosciuta" },
        "reteAssenteSec" to if (persaDal > 0L) (SystemClock.elapsedRealtime() - persaDal) / 1000 else 0,
        "batteria" to ultimoLivello,
        "inCarica" to inCarica,
    )
}
