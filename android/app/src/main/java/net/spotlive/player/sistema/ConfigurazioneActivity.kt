package net.spotlive.player.sistema

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.spotlive.player.BuildConfig
import net.spotlive.player.MainActivity

/**
 * Schermata "Configurazione player": una riga per ogni impostazione Android che serve a un
 * player da negozio, con spunta verde o rossa e un pulsante che apre la pagina giusta.
 * Si apre alla prima apertura (se manca qualcosa) o con 5 tocchi nell'angolo in alto a
 * sinistra + PIN di servizio. Mentre è aperta il watchdog è in pausa.
 */
class ConfigurazioneActivity : Activity() {
    private lateinit var elenco: LinearLayout
    private lateinit var riepilogo: TextView
    private lateinit var scorrimento: ScrollView
    private lateinit var barraPulsanti: LinearLayout
    private var stretta = false
    // 1.0.8: aperta da sola al primo avvio → senza tocchi/tasti per 3 minuti torna al player
    // (su un box LED senza touch, es. Taurus, la schermata restava davanti al codice di abbinamento).
    private val chiusuraAutomatica = Runnable {
        Diario.evento(this, "configurazione automatica chiusa dopo 3 minuti senza tocchi: torno al player")
        tornaAlPlayer()
    }

    private fun rinviaChiusura() {
        handler.removeCallbacks(chiusuraAutomatica)
        if (intent?.getBooleanExtra(EXTRA_AUTOMATICA, false) == true) handler.postDelayed(chiusuraAutomatica, 180_000L)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        rinviaChiusura()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        rinviaChiusura()
        return super.dispatchKeyEvent(event)
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var versoImpostazioni = false
    private val mantieniPausa = object : Runnable {
        override fun run() {
            Configurazione.pausa(this@ConfigurazioneActivity, 5 * 60_000L)
            aggiorna()
            handler.postDelayed(this, 30_000L)
        }
    }

    private data class Voce(
        val titolo: String,
        val spiegazione: String,
        val pulsante: String,
        val ok: (Context) -> Boolean,
        val opzionale: Boolean = false,
        val azione: () -> Unit,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 22))
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        radice.addView(testo("Configurazione player", 26f, Color.WHITE, grassetto = true))
        radice.addView(
            testo(
                "SpotLive Player ${BuildConfig.VERSION_NAME}. Tocca i pulsanti delle righe rosse, una alla volta. " +
                    "Quando tutte le righe principali sono verdi, tocca «Torna al player».",
                15f,
                Color.rgb(200, 200, 210),
            ).also { it.setPadding(0, dp(6), 0, dp(12)) },
        )
        riepilogo = testo("", 17f, Color.WHITE, grassetto = true).also { it.setPadding(0, 0, 0, dp(12)) }
        radice.addView(riepilogo)
        elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(elenco)

        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        barra.addView(pulsante("Torna al player", principale = true) { tornaAlPlayer() })
        barra.addView(pulsante("Manutenzione 10 minuti") {
            Configurazione.pausa(this, 10 * 60_000L)
            apri(Intent(Settings.ACTION_SETTINGS))
        })
        barra.addView(pulsante("Impostazioni Android") { apri(Intent(Settings.ACTION_SETTINGS)) })
        radice.addView(barra)

        scorrimento = ScrollView(this).apply { addView(radice) }
        // 1.0.8: con la zona LED attiva la configurazione sta nello stesso rettangolo 0,0 (si vede sul pannello).
        val esterno = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        esterno.addView(scorrimento, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(esterno)
        barraPulsanti = barra
        applicaZona()
        Configurazione.segnaPrimaConfigurazione(this)
    }

    override fun onResume() {
        super.onResume()
        versoImpostazioni = false
        applicaZona()
        // 5 minuti per sistemare le impostazioni senza che il watchdog riporti su il player.
        if (Configurazione.pausaRestanteMs(this) < 5 * 60_000L) Configurazione.pausa(this, 5 * 60_000L)
        aggiorna()
        handler.postDelayed(mantieniPausa, 30_000L)
        rinviaChiusura()
    }

    override fun onPause() {
        handler.removeCallbacks(mantieniPausa)
        handler.removeCallbacks(chiusuraAutomatica)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Uscita con Home o altro (non verso una pagina delle impostazioni): il player torna subito.
        if (!versoImpostazioni && !isFinishing) {
            Configurazione.finePausa(this)
            Diario.evento(this, "configurazione lasciata: fine pausa")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = tornaAlPlayer()

    private fun tornaAlPlayer() {
        Configurazione.finePausa(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun voci(): List<Voce> = vociBase() + vociProprietario()

    /** Riga visibile solo con il "controllo completo" attivo: permette di toglierlo con il PIN di servizio. */
    private fun vociProprietario(): List<Voce> =
        if (!GestoreDispositivo.proprietario(this)) emptyList()
        else listOf(
            Voce(
                "Controllo completo attivo",
                "SpotLive è proprietario del dispositivo (aggiornamenti senza tocchi, riavvio da remoto, aggiornamenti di sistema di notte). " +
                    "«Rimuovi» lo toglie senza azzerare il tablet: serve il PIN di servizio.",
                "Rimuovi",
                { true },
                opzionale = true,
            ) { chiediRimozioneProprietario() },
        )

    private fun chiediRimozioneProprietario() {
        val campo = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN di servizio"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Rimuovere il controllo completo?")
            .setMessage("Il player resta installato e abbinato, ma gli aggiornamenti torneranno a chiedere «Aggiorna» e il riavvio da remoto non sarà più disponibile. Per riattivarlo servirà di nuovo il cavo o un ripristino.")
            .setView(campo)
            .setPositiveButton("Rimuovi") { _, _ ->
                if (!Configurazione.pinCorretto(campo.text.toString())) {
                    android.widget.Toast.makeText(this, "PIN errato", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val ok = GestoreDispositivo.rimuoviProprietario(this)
                Diario.evento(this, if (ok) "controllo completo rimosso (PIN)" else "rimozione controllo completo NON riuscita")
                android.widget.Toast.makeText(
                    this,
                    if (ok) "Controllo completo rimosso" else "Rimozione non riuscita",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                aggiorna()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun vociBase(): List<Voce> =
        listOf(
            Voce(
                "Avvio automatico",
                "Il player si apre da solo quando il tablet si accende o torna la corrente. È verde quando è attiva «Mostra sopra altre app» oppure SpotLive è l'app Home.",
                "Info app",
                { Configurazione.avvioAutomatico(it) },
            ) { apri(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
            Voce(
                "Mostra sopra altre app",
                "Permette al player di tornare sempre in primo piano, sopra tutto il resto. Nella pagina che si apre attiva l'interruttore per SpotLive Player.",
                "Attiva",
                { Configurazione.sopraAltreApp(it) },
            ) { apri(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
            Voce(
                "Batteria senza limiti",
                "Android non deve mai fermare il player per risparmiare batteria. Alla domanda rispondi «Consenti».",
                "Attiva",
                { Configurazione.batteriaSenzaLimiti(it) },
            ) { chiediBatteria() },
            Voce(
                "App Home",
                "Consigliato: scegli SpotLive Player come app Home. Così il tasto Home riporta al player e all'accensione parte subito.",
                "Scegli",
                { Configurazione.appHome(it) },
            ) { chiediHome() },
            Voce(
                "Notifiche",
                "Serve per l'avviso fisso «Mantiene il player attivo» e per riaprire il player se Android lo blocca.",
                "Consenti",
                { Configurazione.notifiche(it) },
                opzionale = true,
            ) { chiediNotifiche() },
            Voce(
                "Aggiornamenti da remoto",
                "Permesso «Installa app sconosciute» per SpotLive: serve per ricevere le nuove versioni dal server.",
                "Consenti",
                { Configurazione.installazioneApp(it) },
                opzionale = true,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    apri(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
            },
            Voce(
                "Tipo di schermo: " + Configurazione.nomeTipoSchermo(Configurazione.tipoSchermo(this)),
                when (Configurazione.tipoSchermo(this)) {
                    "led" -> "Fuori orario: schermo nero e player in pausa; l'uscita video resta attiva (il pannello LED non mostra «nessun segnale»)."
                    "monitor" -> "Fuori orario: schermo nero, player in pausa e poi standby. Se il box ha HDMI-CEC attivo, il monitor/TV va in standby e si riaccende all'orario."
                    else -> "Fuori orario: schermo nero, luminosità al minimo, player in pausa; poi lo schermo si spegne da solo e si riaccende all'orario."
                } + " Tocca per cambiare.",
                "Cambia",
                { true },
                opzionale = true,
            ) {
                val t = Configurazione.TIPI_SCHERMO
                val i = t.indexOf(Configurazione.tipoSchermo(this))
                Configurazione.impostaTipoSchermo(this, t[(i + 1) % t.size])
                aggiorna()
            },
            Voce(
                "Zona LED: " + ZonaLed.descrizione(this),
                "Per pannelli LED più piccoli dell'uscita video (es. Taurus/Novastar che mostra solo l'angolo in alto a sinistra): " +
                    "il player disegna solo nel rettangolo larghezza×altezza che parte da 0,0. «Automatica» usa la risoluzione " +
                    "dello schermo LED impostata nel pannello SpotLive (dopo l'abbinamento).",
                "Cambia",
                { ZonaLed.modalita(it) != "auto" || ZonaLed.automatica(it) != null },
                opzionale = true,
            ) { scegliZona() },
            Voce(
                "Luminosità LED Novastar (Taurus)",
                "Serve solo sulle Taurus: SpotLive regola la luminosità del LED (giorno/notte dal pannello) tramite il servizio " +
                    "locale della Taurus. Inserisci la password del terminale (quella di ViPlex): resta cifrata solo su questo dispositivo. " +
                    "«Prova» porta il LED al 40% per 1 minuto e poi rimette il valore di prima.",
                "Imposta",
                { (application as net.spotlive.player.PlayerApplicazione).archivioSicuro.passwordTerminale() != null },
                opzionale = true,
            ) { chiediPasswordTaurus() },
            Voce(
                "Blocco sul player (facoltativo)",
                "Fissa il player sullo schermo: per uscire bisogna tenere premuti Indietro e Panoramica. Android chiede conferma a ogni avvio. " +
                    "Prima attiva Impostazioni > Sicurezza > «Blocco app». Tocca di nuovo per spegnerlo.",
                if (Configurazione.bloccoSulPlayer(this)) "Spegni" else "Accendi",
                { Configurazione.bloccoSulPlayer(it) },
                opzionale = true,
            ) {
                Configurazione.impostaBlocco(this, !Configurazione.bloccoSulPlayer(this))
                aggiorna()
            },
        )

    /** Zona LED: la schermata si restringe al rettangolo 0,0 e le righe vanno in colonna se è stretta. */
    private fun applicaZona() {
        if (!::scorrimento.isInitialized) return
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(it)
        }.let { it.widthPixels to it.heightPixels }
        val z = ZonaLed.effettiva(this, b.first, b.second)
        scorrimento.layoutParams =
            if (z == null) android.widget.FrameLayout.LayoutParams(-1, -1)
            else android.widget.FrameLayout.LayoutParams(z.larghezza, z.altezza, Gravity.TOP or Gravity.START)
        stretta = (z?.larghezza ?: b.first) < dp(600)
        val r = scorrimento.getChildAt(0) as? LinearLayout
        if (stretta) r?.setPadding(dp(8), dp(8), dp(8), dp(8)) else r?.setPadding(dp(32), dp(24), dp(32), dp(24))
        barraPulsanti.orientation = if (stretta) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (::elenco.isInitialized) aggiorna()
    }

    private fun scegliZona() {
        val voci = arrayOf(
            "Automatica (dal pannello, solo schermi LED)",
            "Schermo intero (sempre)",
            "256 × 384 (P3.91 verticale)",
            "Altra misura…",
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Zona LED (parte da 0,0 in alto a sinistra)")
            .setItems(voci) { _, i ->
                when (i) {
                    0 -> ZonaLed.impostaModalita(this, "auto")
                    1 -> ZonaLed.impostaModalita(this, "intero")
                    2 -> ZonaLed.impostaManuale(this, ZonaLed.Zona(256, 384))
                    else -> { chiediMisura(); return@setItems }
                }
                Diario.evento(this, "zona LED: ${ZonaLed.descrizione(this)} (configurazione)")
                applicaZona()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun chiediPasswordTaurus() {
        val app = application as net.spotlive.player.PlayerApplicazione
        val campo = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = if (app.archivioSicuro.passwordTerminale() != null) "password salvata (scrivi per cambiarla)" else "password del terminale"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Luminosità LED Novastar")
            .setView(campo)
            .setPositiveButton("Salva") { _, _ ->
                val v = campo.text.toString()
                if (v.isNotEmpty()) {
                    runCatching { app.archivioSicuro.salvaPasswordTerminale(v) }
                    Diario.evento(this, "luminosità: password del terminale salvata dalla configurazione")
                }
                aggiorna()
            }
            .setNeutralButton("Prova 40%") { _, _ ->
                android.widget.Toast.makeText(this, "Prova in corso (1 minuto)…", android.widget.Toast.LENGTH_SHORT).show()
                app.luminosita.prova(40, 60_000L) { esito ->
                    runOnUiThread { android.widget.Toast.makeText(this, esito, android.widget.Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun chiediMisura() {
        val campo = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = "larghezza x altezza, es. 256x384"
            ZonaLed.manuale(this@ConfigurazioneActivity)?.let { setText("${it.larghezza}x${it.altezza}") }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Misura del pannello in pixel")
            .setView(campo)
            .setPositiveButton("Salva") { _, _ ->
                val v = campo.text.toString()
                val esito = if (v.trim().lowercase() in setOf("auto", "intero", "off")) null else ZonaLed.daTesto(this, v)
                if (esito == null) {
                    android.widget.Toast.makeText(this, "Misura non valida: scrivi ad esempio 256x384", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    Diario.evento(this, "zona LED: $esito (configurazione)")
                    applicaZona()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun aggiorna() {
        elenco.removeAllViews()
        for (v in voci()) elenco.addView(riga(v))
        val ok = Configurazione.essenzialiOk(this)
        riepilogo.text =
            if (ok) "✔ Il player è pronto: si avvia da solo e resta in primo piano."
            else "✖ Mancano ancora delle impostazioni (righe rosse)."
        riepilogo.setTextColor(if (ok) VERDE else ROSSO)
    }

    private fun riga(v: Voce): View {
        val ok = v.ok(this)
        val r = LinearLayout(this).apply {
            orientation = if (stretta) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.rgb(32, 32, 40))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        val simbolo = when {
            ok -> "✔"
            v.opzionale -> "○"
            else -> "✖"
        }
        r.addView(
            testo(simbolo, 30f, if (ok) VERDE else if (v.opzionale) GRIGIO else ROSSO, grassetto = true).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), -2)
                gravity = Gravity.CENTER
            },
        )
        val testi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = if (stretta) LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8); marginEnd = dp(12) }
        }
        testi.addView(testo(v.titolo, 18f, Color.WHITE, grassetto = true))
        testi.addView(testo(v.spiegazione, 14f, Color.rgb(180, 180, 190)))
        r.addView(testi)
        r.addView(pulsante(v.pulsante, principale = !ok && !v.opzionale) { v.azione() })
        return r
    }

    private fun chiediBatteria() {
        @SuppressLint("BatteryLife")
        val diretta = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        if (!apri(diretta, avvisa = false)) apri(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun chiediHome() {
        if (apri(Intent(Settings.ACTION_HOME_SETTINGS), avvisa = false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && apri(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), avvisa = false)) return
        // Ultima via: il selettore di Android ("Apri con" per Home → «Sempre»).
        apri(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun chiediNotifiche() {
        val pref = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !pref.getBoolean("notifiche_chieste", false)) {
            pref.edit().putBoolean("notifiche_chieste", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        apri(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        aggiorna()
    }

    private fun apri(intent: Intent, avvisa: Boolean = true): Boolean =
        try {
            Configurazione.pausa(this, 5 * 60_000L)
            versoImpostazioni = true
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (avvisa) android.widget.Toast.makeText(this, "Pagina non disponibile su questo tablet: aprila dalle Impostazioni.", android.widget.Toast.LENGTH_LONG).show()
            false
        } catch (_: SecurityException) {
            false
        }

    private fun testo(s: String, sp: Float, colore: Int, grassetto: Boolean = false) =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(colore)
            if (grassetto) typeface = Typeface.DEFAULT_BOLD
        }

    private fun pulsante(s: String, principale: Boolean = false, azione: () -> Unit) =
        Button(this).apply {
            text = s
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (principale) Color.rgb(0, 122, 255) else Color.rgb(70, 70, 82))
            }
            setPadding(dp(18), dp(10), dp(18), dp(10))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
            setOnClickListener { azione() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_AUTOMATICA = "automatica"
        private val VERDE = Color.rgb(52, 199, 89)
        private val ROSSO = Color.rgb(255, 69, 58)
        private val GRIGIO = Color.rgb(140, 140, 150)
    }
}
