package net.spotlive.player.sistema

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Provisioning da QR alla prima accensione (Android 10+, obbligatorio da Android 12).
 * Android, dopo aver scaricato e installato l'APK dal QR, chiede all'app che tipo di gestione
 * vuole: rispondiamo «dispositivo completamente gestito» (device owner). Le due activity sono
 * protette da BIND_DEVICE_ADMIN: le può aprire solo il sistema. Non attivano nulla da sole:
 * fuori dal provisioning Android non le chiama mai.
 */
class ModalitaProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK, risultato(intent))
        finish()
    }

    companion object {
        /** Risposta per GET_PROVISIONING_MODE: «completamente gestito» se Android lo consente. */
        fun risultato(richiesta: Intent?): Intent {
            val gestito = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            val consentite =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    richiesta?.getIntegerArrayListExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
                } else {
                    null
                }
            val modo = if (consentite.isNullOrEmpty() || gestito in consentite) gestito else consentite.first()
            return Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, modo)
        }
    }
}

/** ADMIN_POLICY_COMPLIANCE: ultimo passo del provisioning. Applica la configurazione e conferma. */
class ConformitaProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { GestoreDispositivo.configuraSeProprietario(this) }
        Diario.evento(this, "provisioning QR completato")
        setResult(RESULT_OK)
        finish()
    }
}
