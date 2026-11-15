package net.spotlive.player.sistema

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import net.spotlive.player.MainActivity

/**
 * Ricevitore "amministratore del dispositivo". È solo dichiarato: non fa nulla finché il
 * dispositivo non viene configurato con SpotLive come proprietario (device owner) tramite
 * QR alla prima accensione o adb su dispositivo azzerato. Vedi docs/device-owner.md.
 */
class AmministratoreDispositivo : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        GestoreDispositivo.configuraSeProprietario(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        GestoreDispositivo.configuraSeProprietario(context)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Funzioni da proprietario del dispositivo. Tutte NO-OP se l'app non è device owner. */
object GestoreDispositivo {
    private const val TAG = "SpotLiveDO"

    fun componente(c: Context) = ComponentName(c, AmministratoreDispositivo::class.java)

    fun proprietario(c: Context): Boolean =
        runCatching { c.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(c.packageName) }
            .getOrDefault(false)

    /**
     * Da proprietario: player come Home fissa, blocco sul player senza conferma, schermo sempre
     * acceso in carica, niente blocco schermo, aggiornamenti di sistema in finestra notturna.
     * L'installazione silenziosa degli aggiornamenti SpotLive non richiede altro: con device owner
     * PackageInstaller installa senza la finestra "Aggiorna".
     */
    fun configuraSeProprietario(c: Context) {
        if (!proprietario(c)) return
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val admin = componente(c)
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(c.packageName)) }.onFailure { Log.w(TAG, "lockTask", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Nessun pulsante o notifica di sistema durante il blocco (solo il player).
            runCatching { dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE) }
        }
        runCatching {
            val filtro = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, filtro, ComponentName(c, MainActivity::class.java))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setKeyguardDisabled(admin, true) }
            runCatching { dpm.setStatusBarDisabled(admin, true) }
            runCatching {
                // Aggiornamenti di sistema solo tra le 03:00 e le 05:00.
                dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createWindowedInstallPolicy(180, 300))
            }
        }
        runCatching {
            // Schermo sempre acceso con alimentatore (AC | USB | wireless).
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        Log.i(TAG, "configurazione proprietario applicata")
    }

    /** Riavvio del dispositivo (solo proprietario, Android 7+). */
    fun riavvia(c: Context): Boolean {
        if (!proprietario(c) || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        c.getSystemService(DevicePolicyManager::class.java).reboot(componente(c))
        return true
    }

    /**
     * Rimuove il "controllo completo" (device owner) senza azzerare il dispositivo: annulla le
     * impostazioni da proprietario e chiama clearDeviceOwnerApp. Chiamata solo dalla schermata
     * Configurazione dopo il PIN di servizio. Restituisce false se l'app non è proprietaria.
     */
    fun rimuoviProprietario(c: Context): Boolean {
        if (!proprietario(c)) return false
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val admin = componente(c)
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, c.packageName) }
        runCatching { dpm.setLockTaskPackages(admin, arrayOf()) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { dpm.setKeyguardDisabled(admin, false) }
            runCatching { dpm.setStatusBarDisabled(admin, false) }
            runCatching { dpm.setSystemUpdatePolicy(admin, null) }
        }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        return runCatching {
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(c.packageName)
            Log.i(TAG, "proprietario rimosso")
            !proprietario(c)
        }.onFailure { Log.w(TAG, "rimozione proprietario", it) }.getOrDefault(false)
    }

    /** Spegnimento schermo "programmato": blocca lo schermo (lo schermo si spegne). */
    fun spegniSchermo(c: Context): Boolean {
        if (!proprietario(c)) return false
        c.getSystemService(DevicePolicyManager::class.java).lockNow()
        return true
    }
}
