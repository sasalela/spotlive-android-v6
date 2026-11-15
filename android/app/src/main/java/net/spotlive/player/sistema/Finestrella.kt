package net.spotlive.player.sistema

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import net.spotlive.player.piattaforma.servizio

/**
 * Finestrella trasparente di 1 px sopra le altre app (serve il permesso "Mostra sopra altre app").
 * - Finché c'è, Android considera SpotLive "visibile" e gli permette di riaprire il player e di
 *   avviare il servizio in primo piano anche da sottofondo (accensione, dopo un crash, dopo Home).
 * - In orario di accensione tiene lo schermo acceso anche se davanti c'è un'altra app, così il
 *   timeout di Android non spegne il pannello mentre il watchdog riporta su il player.
 * Viene creata appena parte il processo (PlayerApplicazione) e ricontrollata dal watchdog.
 */
object Finestrella {
    private var vista: View? = null
    private var schermoAcceso: Boolean? = null
    var ultimoErrore: String? = null
        private set

    // SVILUPPO-20261114-02: TYPE_APPLICATION_OVERLAY esiste da API 26; prima l'overlay è TYPE_PHONE.
    fun tipoFinestra(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun wm(c: Context): WindowManager = servizio(c, Context.WINDOW_SERVICE)

    private fun parametri(tieniAcceso: Boolean) = WindowManager.LayoutParams(
        1, 1,
        tipoFinestra(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (tieniAcceso) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // alpha 1 con contenuto trasparente: per Android è una finestra davvero visibile.
        alpha = 1f
        title = "SpotLiveFinestrella"
    }

    /** Lo schermo va tenuto acceso? In orario sì; fuori orario solo per i pannelli LED. */
    fun deveTenereAcceso(c: Context): Boolean =
        OrarioAccensione.stato(c).acceso || Configurazione.tipoSchermo(c) == "led"

    @Synchronized
    fun aggiorna(c: Context) {
        val app = c.applicationContext
        val consentito = Configurazione.sopraAltreApp(app)
        val wm = wm(app)
        if (consentito && vista == null) {
            val tieni = deveTenereAcceso(app)
            val v = View(app).apply { setBackgroundColor(Color.TRANSPARENT) }
            runCatching { wm.addView(v, parametri(tieni)); vista = v; schermoAcceso = tieni; ultimoErrore = null }
                .onSuccess { Diario.evento(app, "finestrella aggiunta (tieni acceso=$tieni)") }
                .onFailure {
                    ultimoErrore = it.javaClass.simpleName + ": " + it.message
                    Diario.evento(app, "finestrella NON aggiunta: $ultimoErrore")
                }
        } else if (consentito && vista != null) {
            val tieni = deveTenereAcceso(app)
            if (tieni != schermoAcceso) {
                runCatching { wm.updateViewLayout(vista, parametri(tieni)); schermoAcceso = tieni }
                Diario.evento(app, "finestrella: tieni acceso=$tieni")
            }
        } else if (!consentito && vista != null) {
            togli(app)
            Diario.evento(app, "finestrella tolta: permesso «Mostra sopra altre app» revocato")
        }
    }

    @Synchronized
    fun togli(c: Context) {
        val v = vista ?: return
        runCatching { wm(c.applicationContext).removeView(v) }
        vista = null
        schermoAcceso = null
    }

    fun presente(): Boolean = vista != null && vista?.isAttachedToWindow == true

    fun riepilogo(): Map<String, Any?> = mapOf(
        "presente" to presente(),
        "visibileAndroid" to (vista?.windowVisibility == View.VISIBLE),
        "tieniAcceso" to schermoAcceso,
        "errore" to ultimoErrore,
    )
}
