package net.spotlive.player.sistema

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import net.spotlive.player.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Orari di accensione dello schermo (tabella orario_accensione del pannello, arrivano nel manifest:
 * regole.accensione + eccezioni "chiuso"/"orari_diversi" + schermo.fuso). Stesse regole del player web.
 *
 * Senza proprietario del dispositivo: fuori orario schermo nero, luminosità minima, player in pausa
 * e lo schermo si spegne da solo col timeout di Android; all'orario di accensione una sveglia
 * riaccende lo schermo e riprende il player. Con proprietario del dispositivo lo schermo si spegne subito.
 * Se gli orari non sono noti (manifest mai ricevuto) il player resta SEMPRE acceso.
 */
object OrarioAccensione {
    private const val PREF = "spotlive_orario"
    private const val CHIAVE = "regole"
    private const val TAG = "SpotLiveOrario"
    private val GIORNI = arrayOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")

    /** Script per leggere dal player web solo la parte orari del manifest in cache. */
    const val SCRIPT_LETTURA =
        "(function(){try{var m=JSON.parse(localStorage.getItem('spotlive-manifest-cache')||'null');" +
            "if(!m||!m.regole||!m.schermo)return null;return {fuso:m.schermo.fuso,accensione:m.regole.accensione," +
            "eccezioni:(m.regole.eccezioni||[]).filter(function(e){return e.tipo!=='playlist_diversa'})" +
            ".map(function(e){return {dal:e.dal,al:e.al,tipo:e.tipo,orari:e.orari||[]}})};}catch(e){return null}})()"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Salva le regole lette dal player web; true se sono cambiate. */
    fun salva(c: Context, json: String?): Boolean {
        if (json.isNullOrBlank() || json == "null") return false
        val ok = runCatching { JSONObject(json).getJSONObject("accensione"); true }.getOrDefault(false)
        if (!ok) return false
        if (prefs(c).getString(CHIAVE, null) == json) return false
        prefs(c).edit().putString(CHIAVE, json).apply()
        Log.i(TAG, "orari aggiornati")
        return true
    }

    private data class Intervallo(val inizio: Long, val fine: Long)

    @SuppressLint("NewApi")
    private fun intervalli(regole: JSONObject, daMs: Long, giorni: Int): List<Intervallo> {
        val zona = runCatching { ZoneId.of(regole.optString("fuso", "Europe/Rome")) }.getOrDefault(ZoneId.of("Europe/Rome"))
        val accensione = regole.getJSONObject("accensione")
        val eccezioni = regole.optJSONArray("eccezioni") ?: JSONArray()
        val oggi = Instant.ofEpochMilli(daMs).atZone(zona).toLocalDate()
        val out = ArrayList<Intervallo>()
        for (k in -1..giorni) {
            val d = oggi.plusDays(k.toLong())
            val ds = d.toString()
            val del = (0 until eccezioni.length()).map { eccezioni.getJSONObject(it) }
                .filter { it.optString("dal") <= ds && ds <= it.optString("al") }
            val orari = ArrayList<Pair<String, String>>()
            if (del.any { it.optString("tipo") == "chiuso" }) {
                // giorno chiuso
            } else if (del.any { it.optString("tipo") == "orari_diversi" }) {
                del.filter { it.optString("tipo") == "orari_diversi" }.forEach { e ->
                    val o = e.optJSONArray("orari") ?: JSONArray()
                    for (i in 0 until o.length()) {
                        val x = o.getJSONObject(i); orari += x.optString("apre") to x.optString("chiude")
                    }
                }
            } else {
                val a = accensione.optJSONArray(GIORNI[d.dayOfWeek.value - 1]) ?: JSONArray()
                for (i in 0 until a.length()) {
                    val x = a.getJSONArray(i); orari += x.getString(0) to x.getString(1)
                }
            }
            for ((apre, chiude) in orari) {
                val a = minuti(apre) ?: continue
                val c = minuti(chiude) ?: continue
                val inizio = istante(d, a, zona)
                val fine = if (c > a) istante(d, c, zona) else istante(d.plusDays(1), c, zona)
                out += Intervallo(inizio, fine)
            }
        }
        // unisce gli intervalli che si toccano o sovrappongono
        val ord = out.sortedBy { it.inizio }
        val uniti = ArrayList<Intervallo>()
        for (i in ord) {
            val u = uniti.lastOrNull()
            if (u != null && i.inizio <= u.fine) uniti[uniti.size - 1] = Intervallo(u.inizio, maxOf(u.fine, i.fine))
            else uniti += i
        }
        return uniti
    }

    private fun minuti(s: String): Int? {
        val p = s.split(":"); if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    @SuppressLint("NewApi")
    private fun istante(d: LocalDate, minuti: Int, zona: ZoneId): Long =
        if (minuti >= 1440) d.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        else d.atTime(LocalTime.of(minuti / 60, minuti % 60)).atZone(zona).toInstant().toEpochMilli()

    /** Stato attuale: acceso, e prossimo cambio (ms epoch, null = nessun cambio nei prossimi 8 giorni). */
    data class Stato(val noto: Boolean, val acceso: Boolean, val prossimoCambio: Long?)

    fun stato(c: Context, ora: Long = System.currentTimeMillis()): Stato {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Stato(false, true, null)
        val raw = prefs(c).getString(CHIAVE, null) ?: return Stato(false, true, null)
        return runCatching {
            val lista = intervalli(JSONObject(raw), ora, 8)
            val dentro = lista.firstOrNull { it.inizio <= ora && ora < it.fine }
            if (dentro != null) Stato(true, true, dentro.fine)
            else Stato(true, false, lista.firstOrNull { it.inizio > ora }?.inizio)
        }.getOrElse { Log.w(TAG, "orari non leggibili", it); Stato(false, true, null) }
    }

    fun riepilogo(c: Context): Map<String, Any?> {
        val s = stato(c)
        return mapOf(
            "orariNoti" to s.noto,
            "acceso" to s.acceso,
            "prossimoCambio" to s.prossimoCambio?.let { java.util.Date(it).toString() },
        )
    }

    /** Programma la sveglia al prossimo cambio (accensione o spegnimento). */
    fun programma(c: Context) {
        val app = c.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            app, 7301, Intent(app, OrarioRicevitore::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val quando = stato(app).prossimoCambio
        if (quando == null) { am.cancel(pi); return }
        val t = quando + 1000
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                val mostra = PendingIntent.getActivity(
                    app, 7302, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, mostra), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        }.onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) } }
    }

    /** Riaccende lo schermo e riporta davanti il player (usato all'orario di accensione). */
    @Suppress("DEPRECATION")
    fun svegliaSchermo(c: Context) {
        val app = c.applicationContext
        runCatching {
            val pm = app.getSystemService(PowerManager::class.java)
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "spotlive:accensione",
            ).acquire(15_000L)
        }
        Finestrella.aggiorna(app)
        MainActivity.permettiAccensioneSchermo()
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }
}

/** Sveglia degli orari: all'accensione riaccende lo schermo, allo spegnimento aggiorna il player. */
class OrarioRicevitore : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent?) {
        val s = OrarioAccensione.stato(c)
        Log.i("SpotLiveOrario", "sveglia: acceso=${s.acceso}")
        if (s.acceso) OrarioAccensione.svegliaSchermo(c)
        else if (c.getSystemService(PowerManager::class.java).isInteractive) runCatching {
            c.applicationContext.startActivity(
                Intent(c.applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
        OrarioAccensione.programma(c)
    }
}
