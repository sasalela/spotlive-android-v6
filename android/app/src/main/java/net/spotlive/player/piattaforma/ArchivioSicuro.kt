package net.spotlive.player.piattaforma

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Archivio per il token del dispositivo, cifrato con Keystore tramite EncryptedSharedPreferences.
 */
class ArchivioSicuro(contesto: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey =
            MasterKey.Builder(contesto).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs =
            EncryptedSharedPreferences.create(
                contesto,
                NOME_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }

    fun leggi(chiave: String): Pair<Boolean, String?> {
        if (chiave != CHIAVE_TOKEN) return Pair(false, null)
        val valore = prefs.getString(CHIAVE_TOKEN, null)
        return Pair(valore != null, valore)
    }

    fun scrivi(chiave: String, valore: String) {
        if (chiave != CHIAVE_TOKEN) throw IllegalArgumentException("chiave non supportata")
        prefs.edit().putString(CHIAVE_TOKEN, valore).apply()
    }

    fun cancella(chiave: String) {
        if (chiave != CHIAVE_TOKEN) throw IllegalArgumentException("chiave non supportata")
        prefs.edit().remove(CHIAVE_TOKEN).apply()
    }

    // 1.0.15: password del terminale Taurus (servizio locale di gestione). Cifrata con Keystore come il token.
    // Solo per il codice nativo: il ponte JavaScript accetta ancora soltanto la chiave "token",
    // quindi la pagina non può né leggerla né sovrascriverla. Non va mai in log, stato o telemetria.
    fun passwordTerminale(): String? = prefs.getString(CHIAVE_PASSWORD_TERMINALE, null)

    fun salvaPasswordTerminale(valore: String) {
        require(valore.isNotEmpty() && valore.length <= 128) { "password non valida" }
        prefs.edit().putString(CHIAVE_PASSWORD_TERMINALE, valore).commit()
    }

    fun cancellaPasswordTerminale() {
        prefs.edit().remove(CHIAVE_PASSWORD_TERMINALE).commit()
    }

    companion object {
        const val CHIAVE_TOKEN = "token"
        private const val CHIAVE_PASSWORD_TERMINALE = "taurus_password_terminale"
        private const val NOME_FILE = "spotlive_archivio_sicuro"
    }
}
