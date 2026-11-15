package net.spotlive.player.luminosita

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Regola di luminosità dal manifest (campo `luminosita`, PROGETTO §1.5).
 * Il campo assente significa "gestione spenta": in quel caso nessuno chiama mai il LED.
 */
data class RegolaLuminosita(
    val modo: String,
    val giornoPct: Int,
    val nottePct: Int,
    val spostaTramontoMin: Int,
    val spostaAlbaMin: Int,
    val notteDalleMin: Int,
    val giornoDalleMin: Int,
    val lat: Double?,
    val lon: Double?,
)

data class LuminositaAttesa(
    val pct: Int,
    val fase: String,
    val finale: Int,
    val inizioFaseMs: Long,
    val albaMs: Long?,
    val tramontoMs: Long?,
)

/** Modulo puro: niente I/O, niente Android. Stesso algoritmo e stessi casi di prova di player-desktop. */
object CalcoloLuminosita {
    const val GRADINI = 4
    const val DURATA_PASSAGGIO_MS = 20 * 60_000L
    private const val ZENIT = 90.833

    fun daManifest(o: JsonObject?): RegolaLuminosita? {
        if (o == null) return null
        fun i(k: String, d: Int) = runCatching { o[k]?.jsonPrimitive?.intOrNull }.getOrNull() ?: d
        fun dbl(k: String) = runCatching { o[k]?.jsonPrimitive?.doubleOrNull }.getOrNull()
        fun s(k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        // "attiva": false esplicito vale come spento (il server di norma toglie il campo).
        if (runCatching { o["attiva"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == false) return null
        val lat = dbl("lat")?.takeIf { it in -90.0..90.0 }
        val lon = dbl("lon")?.takeIf { it in -180.0..180.0 }
        return RegolaLuminosita(
            modo = if (s("modo") == "orari") "orari" else "sole",
            giornoPct = i("giornoPct", 90).coerceIn(5, 100),
            nottePct = i("nottePct", 20).coerceIn(5, 100),
            spostaTramontoMin = i("spostaTramontoMin", 0).coerceIn(-120, 120),
            spostaAlbaMin = i("spostaAlbaMin", 0).coerceIn(-120, 120),
            notteDalleMin = minuti(s("notteDalle")) ?: (19 * 60),
            giornoDalleMin = minuti(s("giornoDalle")) ?: (7 * 60),
            lat = if (lat != null && lon != null) lat else null,
            lon = if (lat != null && lon != null) lon else null,
        )
    }

    fun minuti(hhmm: String?): Int? {
        val m = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").find(hhmm ?: return null) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    /**
     * Alba o tramonto (UTC, ms) per il giorno civile [anno,mese,giorno] nel punto dato.
     * Algoritmo NOAA semplificato (Almanac for Computers), zenit 90,833°: ±1–2 min in Italia.
     * null = il sole non sorge o non tramonta quel giorno.
     */
    fun sole(anno: Int, mese: Int, giorno: Int, lat: Double, lon: Double, alba: Boolean): Long? {
        val n = giornoAnno(anno, mese, giorno)
        val lngOra = lon / 15.0
        val t = n + ((if (alba) 6.0 else 18.0) - lngOra) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634)
        var ra = norm360(deg(atan(0.91764 * tan(rad(l)))))
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(ZENIT)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (alba) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val tl = h + ra - 0.06571 * t - 6.622
        var ut = (tl - lngOra) % 24.0
        if (ut < 0) ut += 24.0
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(anno, mese - 1, giorno, 0, 0, 0)
        }
        return c.timeInMillis + (ut * 3_600_000.0).toLong()
    }

    /** Luminosità da applicare adesso, compreso il passaggio graduale di 4 gradini in 20 minuti. */
    fun attesa(r: RegolaLuminosita, fuso: String, adessoMs: Long): LuminositaAttesa {
        val zona = TimeZone.getTimeZone(fuso)
        // Elenco dei cambi (inizio giorno / inizio notte) da ieri a domani, in ordine.
        val cambi = mutableListOf<Pair<Long, String>>()
        var alba: Long? = null
        var tramonto: Long? = null
        val usaSole = r.modo == "sole" && r.lat != null && r.lon != null
        for (d in -1..1) {
            val c = Calendar.getInstance(zona).apply { timeInMillis = adessoMs; add(Calendar.DAY_OF_MONTH, d) }
            val (y, mo, g) = Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            var a: Long? = null
            var t: Long? = null
            if (usaSole) {
                a = sole(y, mo, g, r.lat!!, r.lon!!, true)?.plus(r.spostaAlbaMin * 60_000L)
                t = sole(y, mo, g, r.lat, r.lon, false)?.plus(r.spostaTramontoMin * 60_000L)
                if (d == 0) { alba = a; tramonto = t }
            }
            if (a == null || t == null) {
                // Senza posizione (o giorno/notte polare): orari fissi.
                a = locale(zona, y, mo, g, r.giornoDalleMin)
                t = locale(zona, y, mo, g, r.notteDalleMin)
            }
            cambi += a to "giorno"
            cambi += t to "notte"
        }
        cambi.sortBy { it.first }
        val ultimo = cambi.lastOrNull { it.first <= adessoMs } ?: (cambi.first().first to if (cambi.first().second == "giorno") "notte" else "giorno")
        val prima = cambi.lastOrNull { it.first < ultimo.first }
        val fase = ultimo.second
        val finale = if (fase == "giorno") r.giornoPct else r.nottePct
        val partenza = if (prima != null && prima.second != fase) (if (fase == "giorno") r.nottePct else r.giornoPct) else finale
        return LuminositaAttesa(
            pct = gradino(partenza, finale, ultimo.first, adessoMs),
            fase = fase,
            finale = finale,
            inizioFaseMs = ultimo.first,
            albaMs = alba,
            tramontoMs = tramonto,
        )
    }

    /** Gradino k di 4 (uno ogni 5 minuti): al minuto 0 un quarto della strada, dal minuto 15 il valore finale. */
    fun gradino(da: Int, a: Int, inizioMs: Long, adessoMs: Long): Int {
        if (da == a) return a
        val trascorso = adessoMs - inizioMs
        if (trascorso < 0 || trascorso >= DURATA_PASSAGGIO_MS) return a
        val k = minOf(GRADINI.toLong(), trascorso / (DURATA_PASSAGGIO_MS / GRADINI) + 1).toInt()
        return Math.round(da + (a - da) * k / GRADINI.toDouble()).toInt()
    }

    private fun locale(zona: TimeZone, y: Int, mo: Int, g: Int, minuti: Int): Long =
        Calendar.getInstance(zona).apply {
            clear()
            set(y, mo - 1, g, minuti / 60, minuti % 60, 0)
        }.timeInMillis

    private fun giornoAnno(y: Int, m: Int, d: Int): Int {
        val n1 = floor(275.0 * m / 9).toInt()
        val n2 = floor((m + 9) / 12.0).toInt()
        val n3 = 1 + floor((y - 4 * floor(y / 4.0) + 2) / 3.0).toInt()
        return n1 - n2 * n3 + d - 30
    }

    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)
    private fun norm360(x: Double): Double { var v = x % 360.0; if (v < 0) v += 360.0; return v }
}
