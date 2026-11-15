package net.spotlive.player.sistema

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stato della configurazione del player sul dispositivo (le "spunte verdi") e piccole
 * preferenze locali: pausa manutenzione del watchdog e blocco sul player opzionale.
 */
object Configurazione {
    private const val PREF = "spotlive_configurazione"
    private const val SALE_PIN = "spotlive-player-servizio-v1"
    // SHA-256(SALE_PIN + PIN di servizio). Il PIN non è nel codice.
    private const val HASH_PIN = "2e513a2ac1fd6fe59286fe7e82b408c9c0802147b5e9004d4bad258dfa980be3"

    /** "Mostra sopra altre app": permette di riportare il player in primo piano da sottofondo. */
    fun sopraAltreApp(c: Context): Boolean = Settings.canDrawOverlays(c)

    /** "Batteria senza limiti": esenzione dall'ottimizzazione della batteria. */
    fun batteriaSenzaLimiti(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** Il player è l'app Home predefinita (il tasto Home riporta al player, parte da solo all'accensione). */
    fun appHome(c: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == c.packageName
    }

    /** Pacchetto che Android apre col tasto Home (diagnostica). */
    fun homePredefinita(c: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.let { it.packageName + "/" + it.name.substringAfterLast('.') }
    }

    fun notifiche(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

    fun installazioneApp(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || c.packageManager.canRequestPackageInstalls()

    /**
     * Avvio automatico affidabile: il ricevitore di accensione c'è sempre, ma da Android 10
     * l'app può aprire lo schermo da sottofondo solo se è l'app Home o se può mostrarsi sopra le altre.
     */
    fun avvioAutomatico(c: Context): Boolean = appHome(c) || sopraAltreApp(c) || GestoreDispositivo.proprietario(c)

    /** Le spunte indispensabili per un player da negozio. */
    fun essenzialiOk(c: Context): Boolean = avvioAutomatico(c) && sopraAltreApp(c) && batteriaSenzaLimiti(c)

    fun riepilogo(c: Context): Map<String, Any?> =
        mapOf(
            "avvioAutomatico" to avvioAutomatico(c),
            "sopraAltreApp" to sopraAltreApp(c),
            "batteriaSenzaLimiti" to batteriaSenzaLimiti(c),
            "appHome" to appHome(c),
            "notifiche" to notifiche(c),
            "bloccoSulPlayer" to bloccoSulPlayer(c),
            "proprietarioDispositivo" to GestoreDispositivo.proprietario(c),
            "pausaManutenzioneSec" to (pausaRestanteMs(c) / 1000),
            "orario" to OrarioAccensione.riepilogo(c),
            "tipoSchermo" to tipoSchermo(c),
            "zonaLed" to ZonaLed.riepilogo(c),
            "homePredefinita" to homePredefinita(c),
            "controllo" to WatchdogServizio.riepilogo(),
            "diario" to Diario.ultimi(c, 40),
        )

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // --- pausa manutenzione: il watchdog non riporta in primo piano il player ---
    // Basata su elapsedRealtime: un riavvio annulla la pausa (il player deve sempre ripartire).
    fun pausa(c: Context, durataMs: Long) {
        prefs(c).edit().putLong("pausa_fino", SystemClock.elapsedRealtime() + durataMs).apply()
    }

    fun finePausa(c: Context) = prefs(c).edit().remove("pausa_fino").apply()

    fun pausaRestanteMs(c: Context): Long =
        PausaAssistenza.restanteMs(prefs(c).getLong("pausa_fino", 0), SystemClock.elapsedRealtime())

    // --- blocco sul player (fissa app / lock task), opzionale e spento di serie ---
    fun bloccoSulPlayer(c: Context): Boolean = prefs(c).getBoolean("blocco_player", false)

    fun impostaBlocco(c: Context, attivo: Boolean) = prefs(c).edit().putBoolean("blocco_player", attivo).apply()

    // --- tipo di schermo (decide cosa fare fuori orario) ---
    // "led": schermo nero ma uscita video sempre attiva (niente "nessun segnale" sul pannello LED);
    // "monitor": schermo nero e poi standby del dispositivo (con HDMI-CEC del box il monitor/TV va in standby);
    // "tablet": schermo nero, luminosità minima, poi lo schermo del tablet si spegne col timeout di Android.
    val TIPI_SCHERMO = listOf("led", "monitor", "tablet")

    fun tipoSchermo(c: Context): String =
        prefs(c).getString("tipo_schermo", null) ?: run {
            val tv = (c.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            if (tv) "led" else "tablet"
        }

    fun impostaTipoSchermo(c: Context, tipo: String) = prefs(c).edit().putString("tipo_schermo", tipo).apply()

    fun nomeTipoSchermo(tipo: String) = when (tipo) {
        "led" -> "Schermo LED"
        "monitor" -> "Monitor / TV"
        else -> "Tablet"
    }

    // --- prima apertura ---
    fun primaConfigurazioneMostrata(c: Context): Boolean = prefs(c).getBoolean("prima_mostrata", false)

    fun segnaPrimaConfigurazione(c: Context) = prefs(c).edit().putBoolean("prima_mostrata", true).apply()

    fun pinCorretto(pin: String): Boolean {
        val h = MessageDigest.getInstance("SHA-256").digest((SALE_PIN + pin.trim()).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(h.toByteArray(), HASH_PIN.toByteArray())
    }
}
