package com.spotlive.player

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestore centralizzato delle SharedPreferences
 *
 * Gestisce il salvataggio e recupero dei due parametri configurabili dall'utente:
 * - ID_SCHERMO: identificativo univoco dello schermo
 * - USER_SCHERMO: username/identificativo utente
 *
 * Questi valori vengono richiesti al primo avvio e salvati permanentemente sul dispositivo.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Config.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Verifica se l'app è già stata configurata
     *
     * @return true se ID_SCHERMO e USER_SCHERMO sono già salvati
     */
    fun isConfigured(): Boolean {
        val idSchermo = getIdSchermo()
        val userSchermo = getUserSchermo()
        return !idSchermo.isNullOrEmpty() && !userSchermo.isNullOrEmpty()
    }

    /**
     * Salva la configurazione utente
     *
     * @param idSchermo ID univoco dello schermo
     * @param userSchermo Username dello schermo
     */
    fun saveConfiguration(idSchermo: String, userSchermo: String) {
        prefs.edit().apply {
            putString(Config.PREF_KEY_ID_SCHERMO, idSchermo.trim())
            putString(Config.PREF_KEY_USER_SCHERMO, userSchermo.trim())
            putBoolean(Config.PREF_KEY_CONFIGURED, true)
            apply()
        }
    }

    /**
     * Recupera l'ID dello schermo salvato
     *
     * @return ID_SCHERMO o null se non configurato
     */
    fun getIdSchermo(): String? {
        return prefs.getString(Config.PREF_KEY_ID_SCHERMO, null)
    }

    /**
     * Recupera lo username dello schermo salvato
     *
     * @return USER_SCHERMO o null se non configurato
     */
    fun getUserSchermo(): String? {
        return prefs.getString(Config.PREF_KEY_USER_SCHERMO, null)
    }

    /**
     * Cancella tutta la configurazione salvata
     * Utile per reset completo dell'app
     */
    fun clearConfiguration() {
        prefs.edit().clear().apply()
    }

    /**
     * Costruisce l'URL completo per scaricare la programmazione
     * usando i valori salvati
     *
     * @return URL completo o null se non configurato
     */
    fun getProgrammationUrl(): String? {
        val idSchermo = getIdSchermo()
        val userSchermo = getUserSchermo()

        return if (idSchermo != null && userSchermo != null) {
            Config.buildProgrammationUrl(idSchermo, userSchermo)
        } else {
            null
        }
    }

    /**
     * Costruisce l'URL per heartbeat usando i valori salvati
     *
     * @return URL completo o null se non configurato
     */
    fun getUpdateUrl(): String? {
        val idSchermo = getIdSchermo()
        val userSchermo = getUserSchermo()

        return if (idSchermo != null && userSchermo != null) {
            Config.buildUpdateUrl(idSchermo, userSchermo)
        } else {
            null
        }
    }

    /**
     * Dati di configurazione in forma testuale per debug/log
     *
     * @return String descrittivo della configurazione corrente
     */
    fun getConfigurationSummary(): String {
        return buildString {
            appendLine("=== SpotLive Configuration ===")
            appendLine("Configured: ${isConfigured()}")
            appendLine("ID Schermo: ${getIdSchermo() ?: "NON CONFIGURATO"}")
            appendLine("User Schermo: ${getUserSchermo() ?: "NON CONFIGURATO"}")
            appendLine("Server URL: ${Config.SERVER_URL}")
            appendLine("Server Host: ${Config.SERVER_HOST}")
            appendLine("FTP Port: ${Config.FTP_PORT}")
            appendLine("Version: ${Config.VERSION}")
            appendLine("==============================")
        }
    }
}
