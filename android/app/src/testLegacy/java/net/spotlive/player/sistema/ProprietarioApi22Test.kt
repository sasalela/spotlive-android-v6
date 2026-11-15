// SVILUPPO-20261114-02: ProprietarioTest ripetuto su sdk 22 (Taurus T3, Android 5.1).
package net.spotlive.player.sistema

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22], application = android.app.Application::class)
class ProprietarioApi22Test {
    private val c: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun provisioningSceglieDispositivoGestito() {
        val richiesta = Intent(DevicePolicyManager.ACTION_GET_PROVISIONING_MODE).putIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES,
            arrayListOf(DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE),
        )
        val r = ModalitaProvisioningActivity.risultato(richiesta)
        assertEquals(DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE, r.getIntExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, -1))
    }

    @Test fun provisioningSenzaElencoSceglieDispositivoGestito() {
        val r = ModalitaProvisioningActivity.risultato(null)
        assertEquals(DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE, r.getIntExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, -1))
    }

    @Test fun senzaProprietarioNienteRiavvioNeRimozione() {
        assertFalse(GestoreDispositivo.proprietario(c))
        assertFalse(GestoreDispositivo.riavvia(c))
        assertFalse(GestoreDispositivo.rimuoviProprietario(c))
        assertFalse(GestoreDispositivo.spegniSchermo(c))
    }

    @Test fun pinErratoRifiutato() {
        assertFalse(Configurazione.pinCorretto(""))
        assertFalse(Configurazione.pinCorretto("0000"))
    }
}
