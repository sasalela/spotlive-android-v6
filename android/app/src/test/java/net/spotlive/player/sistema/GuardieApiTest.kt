// SVILUPPO-20261114-02: guardie API. sdk 22 = Taurus T3 (Android 5.1); sdk 30 = T30 (Android 11), valori di oggi.
package net.spotlive.player.sistema

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

abstract class GuardieApiBase {
    protected val c: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun configurazioneSenzaEccezioni() {
        Configurazione.sopraAltreApp(c)
        Configurazione.batteriaSenzaLimiti(c)
        Configurazione.notifiche(c)
        Configurazione.riepilogo(c)
    }

    @Test fun orarioSentinellaProprietarioSenzaCrash() {
        OrarioAccensione.programma(c)
        Sentinella.avvia(c)
        assertFalse(GestoreDispositivo.proprietario(c))
    }

    @Test fun watchdogSiAvvia() {
        Robolectric.buildService(WatchdogServizio::class.java).create()
        assertTrue(WatchdogServizio.attivo)
    }

    @Test fun avvioAllAccensione() {
        AvvioRicevitore().onReceive(c, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(Diario.ultimi(c).any { "avvio da BOOT_COMPLETED" in it })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Api30Test : GuardieApiBase() {
    @Test fun valoriDiOggi() {
        assertEquals(Settings.canDrawOverlays(c), Configurazione.sopraAltreApp(c))
        val pm = c.getSystemService(android.os.PowerManager::class.java)
        assertEquals(pm.isIgnoringBatteryOptimizations(c.packageName), Configurazione.batteriaSenzaLimiti(c))
        val nm = c.getSystemService(android.app.NotificationManager::class.java)
        assertEquals(nm.areNotificationsEnabled(), Configurazione.notifiche(c))
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, Finestrella.tipoFinestra())
        assertEquals(NetworkCapabilities.NET_CAPABILITY_VALIDATED, Sentinella.capacitaReteValida())
    }
}
