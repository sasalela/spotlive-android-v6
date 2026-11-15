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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22], application = Application::class)
class Api22Test : GuardieApiBase() {
    @Test fun valoriSottoApi23() {
        // Permesso overlay concesso all'installazione, Doze assente.
        assertTrue(Configurazione.sopraAltreApp(c))
        assertTrue(Configurazione.batteriaSenzaLimiti(c))
        @Suppress("DEPRECATION")
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, Finestrella.tipoFinestra())
        assertEquals(NetworkCapabilities.NET_CAPABILITY_INTERNET, Sentinella.capacitaReteValida())
    }
}
