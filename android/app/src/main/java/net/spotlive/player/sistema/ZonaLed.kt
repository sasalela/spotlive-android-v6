package net.spotlive.player.sistema

import android.content.Context

/**
 * Zona LED (1.0.8): il player disegna solo nel rettangolo che il sending card / ricevitore
 * manda al pannello LED, partendo da x=0, y=0 (in alto a sinistra), come il player Windows.
 *
 * Modalità salvate sul dispositivo:
 *  - "auto"     (predefinita, nessuna impostazione): schermo intero come fino alla 1.0.7.
 *               Dopo l'abbinamento, se il pannello dice che lo schermo è di tipo LED e la sua
 *               risoluzione è più piccola dell'uscita video, la zona si imposta da sola.
 *  - "manuale"  larghezza×altezza scelti dal tecnico (vince su tutto).
 *  - "intero"   schermo intero sempre, anche per gli schermi LED (spegne l'automatico).
 *
 * Si imposta da: Configurazione player (5 tocchi in alto a sinistra + PIN) → «Zona LED»,
 * oppure via adb: am start -n net.spotlive.player/.MainActivity --es zona 256x384
 * (valori: "LxA", "intero", "auto").
 */
object ZonaLed {
    private const val PREF = "spotlive_zona_led"
    const val MIN = 32
    const val MAX = 8192

    data class Zona(val larghezza: Int, val altezza: Int) {
        override fun toString() = "${larghezza}×${altezza}"
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun modalita(c: Context): String = prefs(c).getString("modalita", "auto") ?: "auto"

    fun manuale(c: Context): Zona? = leggi(c, "man_l", "man_a")

    fun automatica(c: Context): Zona? = leggi(c, "auto_l", "auto_a")

    private fun leggi(c: Context, kl: String, ka: String): Zona? {
        val p = prefs(c)
        val l = p.getInt(kl, 0)
        val a = p.getInt(ka, 0)
        return if (l in MIN..MAX && a in MIN..MAX) Zona(l, a) else null
    }

    /** Zona da applicare ora (null = schermo intero). */
    fun effettiva(c: Context, schermoL: Int, schermoA: Int): Zona? {
        val z = when (modalita(c)) {
            "manuale" -> manuale(c)
            "intero" -> null
            else -> automatica(c)
        } ?: return null
        // Una zona grande quanto (o più dell') uscita video equivale a schermo intero.
        if (schermoL > 0 && schermoA > 0 && z.larghezza >= schermoL && z.altezza >= schermoA) return null
        return Zona(
            if (schermoL > 0) minOf(z.larghezza, schermoL) else z.larghezza,
            if (schermoA > 0) minOf(z.altezza, schermoA) else z.altezza,
        )
    }

    fun impostaManuale(c: Context, z: Zona) {
        prefs(c).edit().putString("modalita", "manuale").putInt("man_l", z.larghezza).putInt("man_a", z.altezza).apply()
    }

    fun impostaModalita(c: Context, m: String) {
        require(m == "auto" || m == "intero")
        prefs(c).edit().putString("modalita", m).apply()
    }

    /**
     * Risoluzione ricevuta dal pannello (manifest dello schermo dopo l'abbinamento).
     * Salvata solo per schermi LED più piccoli dell'uscita video; altrimenti si cancella.
     * Ritorna true se la zona automatica è cambiata.
     */
    fun daPannello(c: Context, tipo: String?, l: Int, a: Int, schermoL: Int, schermoA: Int): Boolean {
        val nuova =
            if (tipo == "led" && l in MIN..MAX && a in MIN..MAX && l <= schermoL && a <= schermoA &&
                (l < schermoL || a < schermoA)
            ) Zona(l, a) else null
        if (nuova == automatica(c)) return false
        val e = prefs(c).edit()
        if (nuova == null) e.remove("auto_l").remove("auto_a") else e.putInt("auto_l", nuova.larghezza).putInt("auto_a", nuova.altezza)
        e.apply()
        return true
    }

    /**
     * Valore da intent/adb: "256x384" (anche "256×384", "256,384", "0,0,256,384"), "intero"/"off", "auto".
     * Ritorna una descrizione dell'esito, o null se il valore non è valido.
     */
    fun daTesto(c: Context, valore: String): String? {
        val v = valore.trim().lowercase()
        when (v) {
            "intero", "off", "full", "0" -> { impostaModalita(c, "intero"); return "schermo intero" }
            "auto", "automatica" -> { impostaModalita(c, "auto"); return "automatica" }
        }
        val n = v.split('x', '×', ',', ' ', '*').filter { it.isNotBlank() }.map { it.toIntOrNull() ?: return null }
        val (l, a) = when (n.size) {
            2 -> n[0] to n[1]
            4 -> { if (n[0] != 0 || n[1] != 0) return null; n[2] to n[3] }
            else -> return null
        }
        if (l !in MIN..MAX || a !in MIN..MAX) return null
        impostaManuale(c, Zona(l, a))
        return "manuale ${Zona(l, a)}"
    }

    fun descrizione(c: Context): String = when (modalita(c)) {
        "manuale" -> "Manuale: ${manuale(c) ?: "?"} da 0,0"
        "intero" -> "Schermo intero (sempre)"
        else -> automatica(c)?.let { "Automatica dal pannello: $it da 0,0" } ?: "Automatica: schermo intero (nessuna zona LED dal pannello)"
    }

    fun riepilogo(c: Context): Map<String, Any?> = mapOf(
        "modalita" to modalita(c),
        "manuale" to manuale(c)?.toString(),
        "automatica" to automatica(c)?.toString(),
    )
}
