package net.spotlive.player.sistema

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diario nativo del controllo (watchdog, finestrella, avvii, rilanci): ultimi 80 eventi, salvati
 * sul tablet e inviati nello stato/telemetria ("nativo.diario") e nel log del telecontrollo.
 */
object Diario {
    private const val PREF = "spotlive_diario"
    private const val MAX = 80
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)

    @Synchronized
    fun evento(c: Context, testo: String) {
        Log.i("SpotLiveDiario", testo)
        runCatching {
            val p = c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val a = JSONArray(p.getString("eventi", "[]"))
            a.put(fmt.format(Date()) + " " + testo)
            val da = maxOf(0, a.length() - MAX)
            val b = JSONArray()
            for (i in da until a.length()) b.put(a.getString(i))
            p.edit().putString("eventi", b.toString()).apply()
        }
    }

    fun ultimi(c: Context, n: Int = 40): List<String> = runCatching {
        val a = JSONArray(c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("eventi", "[]"))
        (maxOf(0, a.length() - n) until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
}
