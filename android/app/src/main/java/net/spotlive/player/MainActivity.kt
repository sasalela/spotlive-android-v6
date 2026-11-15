package net.spotlive.player

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import net.spotlive.player.cache.IntercettoreMedia
import net.spotlive.player.ponte.PonteJavascript
import net.spotlive.player.sistema.Configurazione
import net.spotlive.player.sistema.ConfigurazioneActivity
import net.spotlive.player.sistema.WatchdogServizio
import net.spotlive.player.sistema.ZonaLed

class MainActivity : AppCompatActivity() {
    companion object {
        @Volatile
        var visibile: Boolean = false

        /** Codice di abbinamento mostrato dal player web (null se non c'è la schermata di abbinamento). */
        /**
         * Codice letto dalla pagina. Se la pagina è caricata ma non mostra il codice (player già abbinato:
         * il token sta nell'IndexedDB della WebView, non nell'archivio nativo) restituisce [PAGINA_ABBINATA].
         */
        private const val SCRIPT_CODICE =
            "(function(){try{var e=document.querySelector('[data-testid=\"codice-abbinamento\"]');" +
                "if(e)return String(e.textContent||'').trim().slice(0,16);" +
                "var r=document.getElementById('root');" +
                "return (r&&r.childElementCount>0)?'#ABBINATA':null}catch(x){return null}})()"
        private const val PAGINA_ABBINATA = "#ABBINATA"
        private const val PREF_ABB = "spotlive-abbinamento"

        /** Tipo e risoluzione dello schermo dal manifest in cache del player web. */
        private const val SCRIPT_SCHERMO =
            "(function(){try{var m=JSON.parse(localStorage.getItem('spotlive-manifest-cache')||'null');" +
                "if(!m||!m.schermo)return null;return {tipo:m.schermo.tipo||'',l:m.schermo.larghezzaPx|0,a:m.schermo.altezzaPx|0}" +
                "}catch(x){return null}})()"

        private var istanza: java.lang.ref.WeakReference<MainActivity>? = null

        /** Prima di riportare davanti il player all'orario di accensione: permette di riaccendere lo schermo. */
        fun permettiAccensioneSchermo() {
            val a = istanza?.get() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { a.setTurnScreenOn(true) }
        }

        /**
         * La rete è tornata dopo una caduta (anche lunga). 1.0.7: niente più ricaricamento della pagina
         * (prima, oltre 2 minuti, la WebView veniva ricaricata: nero e scaletta ripartita dall'inizio).
         * Il player web continua lo spot in corso e si riallinea da solo (manifest al cambio spot).
         */
        fun reteTornata(assenteSec: Long) {
            val a = istanza?.get() ?: return
            a.runOnUiThread {
                runCatching {
                    if (assenteSec > 120) {
                        net.spotlive.player.sistema.Diario.evento(a, "rete tornata dopo ${assenteSec} s: il player continua senza ricaricare")
                    }
                    a.webView.evaluateJavascript("window.dispatchEvent(new Event('online'))", null)
                }
            }
        }
    }

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var intercettoreMedia: IntercettoreMedia
    private var tocchiAngolo = 0
    private var primoToccoAngolo = 0L
    private var bloccoChiesto = false
    private lateinit var nero: android.view.View
    // 1.0.8 zona LED: la WebView sta nel rettangolo 0,0 larghezza×altezza; riquadro compatto per l'abbinamento.
    private lateinit var radice: android.widget.FrameLayout
    private lateinit var riquadro: android.widget.LinearLayout
    private lateinit var rqTitolo: android.widget.TextView
    private lateinit var rqCodice: android.widget.TextView
    private lateinit var rqStato: android.widget.TextView
    private lateinit var rqNota: android.widget.TextView
    private var zonaApplicata: ZonaLed.Zona? = null
    private var zonaPrimaVolta = true
    private var ultimoCodice: String? = null
    private var codiceLetto: String? = null
    private val cicloAbbinamento = object : Runnable {
        override fun run() {
            controllaAbbinamento()
        }
    }
    private var accesoApplicato: Boolean? = null
    private val gestoreOrario = android.os.Handler(android.os.Looper.getMainLooper())
    private val cicloOrario = object : Runnable {
        override fun run() {
            leggiOrari()
            leggiZonaPannello()
            applicaOrario()
            gestoreOrario.postDelayed(this, 30_000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Si mostra anche sopra la schermata di blocco e accende lo schermo (avvio dopo accensione).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // Accende lo schermo all'avvio, ma non fuori orario (un riavvio dell'app non deve riaccenderlo).
            setTurnScreenOn(net.spotlive.player.sistema.OrarioAccensione.stato(this).acceso)
        } else if (net.spotlive.player.piattaforma.Variante.legacy && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // SVILUPPO-20261114-02, solo legacy: showWhenLocked/turnScreenOn del manifest valgono da API 27.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            if (net.spotlive.player.sistema.OrarioAccensione.stato(this).acceso) {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        // Una sola istanza del player: se ne esiste già una (es. aperta col tasto Home mentre l'altra
        // era in un'altra attività) la più vecchia si chiude, così non ci sono due WebView.
        val vecchia = istanza?.get()
        val daHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        if (vecchia != null && vecchia !== this && !vecchia.isFinishing) {
            net.spotlive.player.sistema.Diario.evento(this, "player aperto di nuovo (home=$daHome): chiudo l'istanza precedente")
            vecchia.finish()
        } else {
            net.spotlive.player.sistema.Diario.evento(this, "player avviato (home=$daHome)")
        }
        istanza = java.lang.ref.WeakReference(this)
        // Il tasto Indietro non chiude il player.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Disegna anche sotto barre e notch: la WebView occupa tutto il pannello.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val app = application as PlayerApplicazione
        intercettoreMedia = IntercettoreMedia(app.cacheNativa)
        assetLoader =
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                .build()

        webView = WebView(this)
        // SVILUPPO-20261114-02, solo legacy: WebView troppo vecchia per il player web → riga fissa e Diario.
        if (BuildConfig.WEBVIEW_CHROME_MIN > 0) diagnosiWebView()
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        // Velo nero per gli orari di spegnimento (sopra la WebView, i tocchi passano al gesto nascosto).
        nero = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = android.view.View.GONE
            isClickable = true
        }
        radice = android.widget.FrameLayout(this)
        radice.setBackgroundColor(android.graphics.Color.BLACK)
        radice.addView(webView, android.widget.FrameLayout.LayoutParams(-1, -1))
        creaRiquadro()
        radice.addView(riquadro, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.START))
        creaStriscia()
        radice.addView(striscia, android.widget.FrameLayout.LayoutParams(-2, -2))
        radice.addView(nero, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(radice)
        nascondiBarre()
        leggiZonaDaIntent(intent)
        applicaZona()
        // Uscita video cambiata (es. risoluzione HDMI): si ricalcola la zona.
        radice.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) radice.post { applicaZona() }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        // Viewport = larghezza del dispositivo in px CSS (meta viewport rispettato), niente zoom né testo ingrandito.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.textZoom = 100
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.setInitialScale(0)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.addJavascriptInterface(PonteJavascript(app.ponteGestore), "SpotlivePonteNativo")
        app.ponteGestore.collega(this, webView)

        // 1.0.12: errori e avvisi della console nella striscia di diagnosi (testo oscurato, mai token).
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage?): Boolean {
                if (m != null && (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR ||
                        m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING)) {
                    val liv = if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) "E" else "W"
                    val src = (m.sourceId() ?: "").substringAfterLast('/').substringBefore('?')
                    DiagnosiLed.aggiungiErrore("$liv ${m.message()} @$src:${m.lineNumber()}")
                }
                return super.onConsoleMessage(m)
            }
        }
        iniettaDiagnosiInizio()
        webView.webViewClient =
            object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    intercettoreMedia.intercetta(request)?.let { return it }
                    osservaManifest(request)
                    // WebView senza DOCUMENT_START_SCRIPT (es. Chrome 83): il passaggio si vede dal lato nativo.
                    if (request.method == "POST" && request.url?.path?.endsWith("/player/passaggi") == true) passaggioVisto = true
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    erroreCaricamento = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectPonteApi()
                }

                // 1.0.10: motivo leggibile nel riquadro quando la pagina o le chiamate al server falliscono.
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: androidx.webkit.WebResourceErrorCompat,
                ) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame && request.url?.path?.contains("/api/") != true) return
                    val codice = runCatching {
                        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) error.errorCode else 0
                    }.getOrDefault(0)
                    val desc = runCatching {
                        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) error.description.toString() else ""
                    }.getOrDefault("")
                    segnalaErrore("${if (request.isForMainFrame) "pagina" else "API"}: ${Diagnosi.erroreWebView(codice)} $desc", request.url)
                }

                // SVILUPPO-20261114-02: sotto API 23 arriva solo questa callback (pagina principale).
                // Da API 23 non fa nulla: lì l'errore lo segnala già la callback qui sopra.
                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        segnalaErrore(
                            "pagina: ${Diagnosi.erroreWebView(errorCode)} ${description ?: ""}",
                            runCatching { android.net.Uri.parse(failingUrl) }.getOrNull(),
                        )
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, risposta: WebResourceResponse) {
                    super.onReceivedHttpError(view, request, risposta)
                    if (!request.isForMainFrame && request.url?.path?.contains("/api/") != true) return
                    // 401 sul manifest senza token è normale solo prima dell'abbinamento: lo si mostra comunque.
                    segnalaErrore("${if (request.isForMainFrame) "pagina" else "API"}: HTTP ${risposta.statusCode} ${risposta.reasonPhrase ?: ""}", request.url)
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                    // SVILUPPO-20261114-02, solo legacy sotto API 24: certificato del pannello già verificato dal client nativo.
                    if (BuildConfig.VARIANTE == "legacy" && Build.VERSION.SDK_INT < Build.VERSION_CODES.N && fiduciaLegacy(error)) {
                        handler.proceed()
                        return
                    }
                    handler.cancel() // mai accettare certificati non validi
                    segnalaErrore("certificato: ${Diagnosi.erroreSsl(error.primaryError)}", runCatching { android.net.Uri.parse(error.url) }.getOrNull())
                }

                // Se il motore della pagina si chiude (poca memoria o crash) l'app non deve morire:
                // si toglie la WebView e si riavvia il player pulito.
                // SVILUPPO-20261114-02: callback e RenderProcessGoneDetail esistono da API 26.
                @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                    android.util.Log.w("SpotLivePlayer", "motore pagina chiuso (crash=${detail.didCrash()}): riavvio")
                    runCatching { (view.parent as? android.view.ViewGroup)?.removeView(view) }
                    runCatching { view.destroy() }
                    net.spotlive.player.sistema.RiavvioActivity.riavvia(this@MainActivity)
                    return true
                }
            }

        // SVILUPPO-20261114-02, solo legacy sotto API 24: il client nativo verifica subito la foglia del pannello.
        if (BuildConfig.VARIANTE == "legacy" && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) verificaFogliaInSottofondo(null)
        webView.loadUrl(urlPlayer())
        injectPonteApi()

        ContextCompat.startForegroundService(this, Intent(this, WatchdogServizio::class.java))

        // Prima apertura: se mancano impostazioni essenziali mostra la configurazione guidata.
        if (!Configurazione.primaConfigurazioneMostrata(this) && !Configurazione.essenzialiOk(this)) {
            apriConfigurazione(automatica = true)
        }
    }

    /** Gesto nascosto: 5 tocchi in 3 s nell'angolo in alto a sinistra, poi PIN di servizio. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val lato = 90 * resources.displayMetrics.density
            if (ev.x < lato && ev.y < lato) {
                val ora = System.currentTimeMillis()
                if (ora - primoToccoAngolo > 3000) {
                    primoToccoAngolo = ora
                    tocchiAngolo = 0
                }
                tocchiAngolo++
                if (tocchiAngolo >= 5) {
                    tocchiAngolo = 0
                    chiediPin()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun chiediPin() {
        Configurazione.pausa(this, 60_000L)
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN di servizio"
        }
        AlertDialog.Builder(this)
            .setTitle("Configurazione player")
            .setView(campo)
            .setPositiveButton("Entra") { _, _ ->
                if (Configurazione.pinCorretto(campo.text.toString())) {
                    apriConfigurazione()
                } else {
                    Configurazione.finePausa(this)
                    android.widget.Toast.makeText(this, "PIN errato", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla") { _, _ -> Configurazione.finePausa(this) }
            .setOnCancelListener { Configurazione.finePausa(this) }
            .show()
    }

    private fun apriConfigurazione(automatica: Boolean = false) {
        Configurazione.pausa(this, 5 * 60_000L)
        if (inBlocco()) runCatching { stopLockTask() }
        startActivity(Intent(this, ConfigurazioneActivity::class.java).putExtra(ConfigurazioneActivity.EXTRA_AUTOMATICA, automatica))
    }

    // SVILUPPO-20261114-02: lockTaskModeState da API 23; sotto isInLockTaskMode (API 21).
    private fun inBlocco(): Boolean {
        val am = net.spotlive.player.piattaforma.servizio<ActivityManager>(this, ACTIVITY_SERVICE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    /** Blocco sul player: automatico se proprietario del dispositivo, altrimenti solo se scelto (con conferma di Android). */
    private fun applicaBlocco() {
        val proprietario = net.spotlive.player.sistema.GestoreDispositivo.proprietario(this)
        val voluto = proprietario || Configurazione.bloccoSulPlayer(this)
        if (!voluto) {
            if (inBlocco()) runCatching { stopLockTask() }
            return
        }
        if (inBlocco() || Configurazione.pausaRestanteMs(this) > 0) return
        if (!proprietario && bloccoChiesto) return // chiedi la conferma una sola volta per avvio
        bloccoChiesto = true
        runCatching { startLockTask() }
    }

    override fun onResume() {
        super.onResume()
        visibile = true
        applicaZona()
        gestoreOrario.removeCallbacks(cicloAbbinamento)
        gestoreOrario.post(cicloAbbinamento)
        net.spotlive.player.sistema.Diario.evento(this, "player davanti")
        nascondiBarre()
        applicaBlocco()
        accesoApplicato = null
        gestoreOrario.removeCallbacks(cicloOrario)
        gestoreOrario.post(cicloOrario)
        gestoreOrario.removeCallbacks(cicloDiagnosi)
        gestoreOrario.post(cicloDiagnosi)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) net.spotlive.player.sistema.Diario.evento(this, "tasto Home: player già davanti")
        leggiZonaDaIntent(intent)
        applicaZona()
        applicaOrario()
    }

    /** Legge dal player web gli orari di accensione del manifest e li salva. */
    private fun leggiOrari() {
        runCatching {
            webView.evaluateJavascript(net.spotlive.player.sistema.OrarioAccensione.SCRIPT_LETTURA) { v ->
                if (net.spotlive.player.sistema.OrarioAccensione.salva(this, v)) applicaOrario()
            }
        }
    }

    /** Fuori orario: nero, luminosità minima, pausa e schermo libero di spegnersi. In orario: tutto normale. */
    private fun applicaOrario() {
        val o = net.spotlive.player.sistema.OrarioAccensione
        val stato = o.stato(this)
        o.programma(this)
        if (accesoApplicato == stato.acceso) return
        accesoApplicato = stato.acceso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(stato.acceso)
        val lp = window.attributes
        if (stato.acceso) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            nero.visibility = android.view.View.GONE
            webView.onResume()
            android.util.Log.i("SpotLiveOrario", "in orario: player acceso")
        } else {
            val tipo = Configurazione.tipoSchermo(this)
            nero.visibility = android.view.View.VISIBLE
            webView.onPause()
            if (tipo == "led") {
                // LED: l'uscita video resta accesa e manda nero (niente "nessun segnale").
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lp.screenBrightness = 0f
                window.attributes = lp
                // Con proprietario del dispositivo lo schermo si spegne subito (e col CEC del box il monitor va
                // in standby); altrimenti si spegne col timeout di Android.
                net.spotlive.player.sistema.GestoreDispositivo.spegniSchermo(this)
            }
            android.util.Log.i("SpotLiveOrario", "fuori orario: schermo nero ($tipo)")
        }
    }

    // ---------------- Zona LED (1.0.8) ----------------

    /** Impostazione della zona da intent/adb: am start -n net.spotlive.player/.MainActivity --es zona 256x384 */
    private fun leggiZonaDaIntent(i: Intent?) {
        val v = i?.getStringExtra("zona") ?: return
        i.removeExtra("zona")
        val esito = ZonaLed.daTesto(this, v)
        val msg = if (esito != null) "zona LED: $esito (da comando)" else "zona LED: valore non valido «${v.take(30)}»"
        net.spotlive.player.sistema.Diario.evento(this, msg)
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    /** Dimensioni dell'uscita video in pixel fisici (tutto lo schermo, barre comprese). */
    private fun dimensioniSchermo(): Pair<Int, Int> {
        if (::radice.isInitialized && radice.width > 0 && radice.height > 0) return radice.width to radice.height
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    /** WebView (e riquadro di abbinamento) nel rettangolo 0,0 larghezza×altezza; null = schermo intero. */
    private fun applicaZona() {
        if (!::radice.isInitialized) return
        val (sl, sa) = dimensioniSchermo()
        val z = ZonaLed.effettiva(this, sl, sa)
        val cambiata = z != zonaApplicata || zonaPrimaVolta
        zonaApplicata = z
        zonaPrimaVolta = false
        if (cambiata) {
            webView.layoutParams =
                if (z == null) android.widget.FrameLayout.LayoutParams(-1, -1)
                else android.widget.FrameLayout.LayoutParams(z.larghezza, z.altezza, android.view.Gravity.TOP or android.view.Gravity.START)
            net.spotlive.player.sistema.Diario.evento(
                this,
                if (z == null) "zona LED: schermo intero (${sl}×${sa})" else "zona LED: ${z} da 0,0 (uscita ${sl}×${sa})",
            )
        }
        dimensionaRiquadro(z, sl, sa)
        dimensionaStriscia(z, sl, sa)
    }

    /** Ogni 30 s: tipo e risoluzione dello schermo dal manifest del pannello (dopo l'abbinamento). */
    private fun leggiZonaPannello() {
        runCatching {
            webView.evaluateJavascript(SCRIPT_SCHERMO) { v ->
                if (v.isNullOrBlank() || v == "null") return@evaluateJavascript
                runCatching {
                    val o = org.json.JSONObject(v)
                    val (sl, sa) = dimensioniSchermo()
                    if (ZonaLed.daPannello(this, o.optString("tipo", ""), o.optInt("l"), o.optInt("a"), sl, sa)) {
                        net.spotlive.player.sistema.Diario.evento(
                            this,
                            "zona LED automatica dal pannello: ${ZonaLed.automatica(this) ?: "nessuna"} (schermo ${o.optString("tipo")} ${o.optInt("l")}×${o.optInt("a")})",
                        )
                        applicaZona()
                    }
                }
            }
        }
    }

    /**
     * 1.0.9: quando la pagina chiede il manifest col token, il guscio lo rilegge da sé (stesso token, solo in
     * memoria) per prendere tipo e risoluzione dello schermo. Non dipende dalla cache della pagina, che viene
     * scritta solo dopo che il player web ha preparato i contenuti. Vale anche come prova di abbinamento.
     */
    @Volatile private var ultimaLetturaManifest = 0L

    private fun osservaManifest(request: WebResourceRequest) {
        val u = request.url ?: return
        if (request.method != "GET" || u.scheme != "https" || u.path?.endsWith("/player/manifest") != true) return
        val attesa = runCatching { android.net.Uri.parse(BuildConfig.URL_PLAYER).host }.getOrNull()
        if (attesa != null && u.host != attesa) return
        val auth = request.requestHeaders?.entries?.firstOrNull { it.key.equals("Authorization", true) }?.value ?: return
        if (!auth.startsWith("Bearer ")) return
        val ora = android.os.SystemClock.elapsedRealtime()
        if (ora - ultimaLetturaManifest < 20_000L) return
        ultimaLetturaManifest = ora
        val url = u.toString()
        Thread {
            val esito = runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                // SVILUPPO-20261114-02: fiducia legacy (CA di sistema + ISRG); nella standard socketFactory() è null.
                net.spotlive.player.piattaforma.Tls.socketFactory()?.let { f -> (c as? javax.net.ssl.HttpsURLConnection)?.sslSocketFactory = f }
                try {
                    c.connectTimeout = 10_000
                    c.readTimeout = 15_000
                    c.setRequestProperty("Authorization", auth)
                    c.setRequestProperty("Accept", "application/json")
                    val codice = c.responseCode
                    manifestNativo = "$codice ${Diagnosi.ora().substring(11, 19)}"
                    if (codice != 200) return@runCatching null
                    val testo = c.inputStream.bufferedReader().use { it.readText() }
                    val o = org.json.JSONObject(testo)
                    val dati = o.optJSONObject("data") ?: o
                    // 1.0.15: luminosità del LED. Campo assente = gestione spenta (nessuna chiamata al LED).
                    runCatching {
                        val lum = dati.optJSONObject("luminosita")
                        val lumJson = lum?.let { kotlinx.serialization.json.Json.parseToJsonElement(it.toString()) as? kotlinx.serialization.json.JsonObject }
                        (application as PlayerApplicazione).luminosita.daManifest(
                            lumJson,
                            dati.optJSONObject("schermo")?.optString("fuso", "")?.takeIf { it.isNotBlank() },
                            lum?.optBoolean("passwordTerminaleDaRitirare", false) == true,
                        )
                    }
                    val sch = dati.optJSONObject("schermo") ?: return@runCatching null
                    Triple(sch.optString("tipo", ""), sch.optInt("larghezzaPx"), sch.optInt("altezzaPx"))
                } finally {
                    c.disconnect()
                }
            }.getOrNull() ?: return@Thread
            gestoreOrario.post { schermoDalManifest(esito.first, esito.second, esito.third) }
        }.apply { isDaemon = true; name = "spotlive-manifest-zona" }.start()
    }

    private fun schermoDalManifest(tipo: String, l: Int, a: Int) {
        if (isFinishing || isDestroyed) return
        val prefs = getSharedPreferences(PREF_ABB, MODE_PRIVATE)
        if (!prefs.getBoolean("pagina_abbinata", false)) {
            prefs.edit().putBoolean("pagina_abbinata", true).apply()
            net.spotlive.player.sistema.Diario.evento(this, "abbinato (manifest ricevuto): riquadro nascosto")
        }
        codiceLetto = null
        mostraRiquadro(null, true)
        val (sl, sa) = dimensioniSchermo()
        if (ZonaLed.daPannello(this, tipo, l, a, sl, sa)) {
            net.spotlive.player.sistema.Diario.evento(
                this,
                "zona LED automatica dal manifest: ${ZonaLed.automatica(this) ?: "nessuna"} (schermo $tipo ${l}×${a}, uscita ${sl}×${sa})",
            )
        }
        applicaZona()
    }

    // 1.0.10: diagnosi nel riquadro (mai il token).
    @Volatile private var erroreCaricamento: String? = null
    @Volatile private var esitoSonda: String? = null
    @Volatile private var sondaInCorso = false
    private var ultimaSonda = 0L

    private fun segnalaErrore(testo: String, url: android.net.Uri?) {
        val u = url?.let { "${it.host}${it.path ?: ""}" } ?: ""
        val t = "$testo · $u".take(160)
        erroreCaricamento = t
        net.spotlive.player.sistema.Diario.evento(this, "errore caricamento: $t")
    }

    // ---------------- SVILUPPO-20261114-02: solo variante legacy (Android 5.1) ----------------

    /** Riga fissa nel riquadro e nella striscia se la WebView è sotto la soglia (null = sufficiente o non nota). */
    @Volatile private var avvisoWebView: String? = null
    @Volatile private var verificaFogliaInCorso = false
    @Volatile private var sslRifiutato = false
    private var fogliaRiverificata: String? = null

    private fun diagnosiWebView() {
        runCatching {
            val ua = runCatching { webView.settings.userAgentString }.getOrNull()
            val chrome = DiagnosiLed.chromeDaUa(ua)
            val p = runCatching { androidx.webkit.WebViewCompat.getCurrentWebViewPackage(this) }.getOrNull()
            val pacchetto = p?.packageName ?: "?"
            val versione = p?.versionName ?: "?"
            val minimo = BuildConfig.WEBVIEW_CHROME_MIN
            val basta = net.spotlive.player.piattaforma.WebViewLegacy.sufficiente(chrome, minimo)
            net.spotlive.player.piattaforma.Variante.webView = mapOf(
                "pacchetto" to pacchetto,
                "versione" to versione,
                "chrome" to chrome,
                "sufficiente" to basta,
            )
            if (basta == false) {
                avvisoWebView = "WebView $pacchetto $versione (Chrome ${net.spotlive.player.piattaforma.WebViewLegacy.chromePrincipale(chrome)}): " +
                    "serve ≥ $minimo. Aggiornare “Android System WebView”"
                net.spotlive.player.sistema.Diario.evento(this, "WebView troppo vecchia: $pacchetto $versione Chrome $chrome, serve ≥ $minimo")
            } else {
                net.spotlive.player.sistema.Diario.evento(this, "WebView $pacchetto $versione Chrome $chrome (soglia $minimo)")
            }
        }
    }

    /** Certificato della WebView rifiutato dalle CA di Android 5.1: accettato solo se è la foglia già verificata. */
    private fun fiduciaLegacy(error: android.net.http.SslError): Boolean {
        val w = net.spotlive.player.piattaforma.WebViewLegacy
        val hostPlayer = runCatching { android.net.Uri.parse(BuildConfig.URL_PLAYER).host }.getOrNull()
        val host = runCatching { android.net.Uri.parse(error.url).host }.getOrNull()
        val errori = (w.SSL_NOTYETVALID..w.SSL_INVALID).filter { runCatching { error.hasError(it) }.getOrDefault(true) }.toSet()
        // Chiave interna AOSP di SslCertificate: se manca la foglia è null e si rifiuta.
        val der = runCatching { android.net.http.SslCertificate.saveState(error.certificate)?.getByteArray("x509-certificate") }.getOrNull()
        val foglia = w.impronta(der)
        val verificata = net.spotlive.player.piattaforma.Tls.fogliaVerificata(hostPlayer)
        val ok = w.puoProcedere(true, error.primaryError, errori, host, hostPlayer, foglia, verificata)
        if (ok) {
            net.spotlive.player.sistema.Diario.evento(this, "certificato pannello accettato (foglia verificata dal client nativo)")
        } else if (host != null && host.equals(hostPlayer, true) && error.primaryError == w.SSL_UNTRUSTED && foglia != null && foglia != verificata) {
            // Foglia nuova (rinnovo) o non ancora verificata: una sola nuova verifica per foglia, poi si ricarica.
            sslRifiutato = true
            verificaFogliaInSottofondo(foglia)
        }
        return ok
    }

    private fun verificaFogliaInSottofondo(fogliaWebView: String?) {
        if (verificaFogliaInCorso) return
        if (fogliaWebView != null) {
            if (fogliaRiverificata == fogliaWebView) return
            fogliaRiverificata = fogliaWebView
        }
        val host = runCatching { android.net.Uri.parse(BuildConfig.URL_PLAYER).host }.getOrNull() ?: return
        verificaFogliaInCorso = true
        Thread {
            val foglia = runCatching { net.spotlive.player.piattaforma.Tls.verificaFoglia(host) }.getOrNull()
            verificaFogliaInCorso = false
            net.spotlive.player.sistema.Diario.evento(applicationContext, "TLS legacy: foglia del pannello ${if (foglia != null) "verificata" else "NON verificata"}")
            if (foglia != null && sslRifiutato) {
                sslRifiutato = false
                gestoreOrario.post { if (!isFinishing && !isDestroyed) runCatching { webView.reload() } }
            }
        }.apply { isDaemon = true; name = "spotlive-tls-foglia" }.start()
    }

    /** Ogni 30 s (solo col riquadro visibile): prova HTTPS nativa verso /player/ e l'API, senza token. */
    private fun sondaServer() {
        val ora = android.os.SystemClock.elapsedRealtime()
        if (sondaInCorso || (ultimaSonda != 0L && ora - ultimaSonda < 30_000L)) return
        ultimaSonda = ora
        sondaInCorso = true
        val base = runCatching { android.net.Uri.parse(BuildConfig.URL_PLAYER) }.getOrNull()
        val origine = if (base?.scheme == "https" && base.host != null) "https://${base.host}" else null
        Thread {
            val esito = if (origine == null) "sonda: URL player non https" else {
                val p = Diagnosi.prova("$origine/player/")
                val a = Diagnosi.prova("$origine/api/v1/player/manifest")
                "sonda ${Diagnosi.ora()}: /player/ $p · API $a"
            }
            esitoSonda = esito
            sondaInCorso = false
            net.spotlive.player.sistema.Diario.evento(applicationContext, esito.take(200))
        }.apply { isDaemon = true; name = "spotlive-sonda" }.start()
    }

    private fun creaRiquadro() {
        fun tv(colore: Int, grassetto: Boolean = false) = android.widget.TextView(this).apply {
            setTextColor(colore)
            includeFontPadding = false
            if (grassetto) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rqTitolo = tv(android.graphics.Color.WHITE, true).apply { text = "SpotLive · collega schermo" }
        rqCodice = tv(android.graphics.Color.WHITE, true).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.rgb(255, 214, 10))
            maxLines = 1
        }
        rqStato = tv(android.graphics.Color.rgb(220, 220, 228))
        rqNota = tv(android.graphics.Color.rgb(150, 150, 160))
        riquadro = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(17, 17, 17))
            visibility = android.view.View.GONE
            addView(rqTitolo)
            addView(rqCodice)
            addView(rqStato)
            addView(rqNota)
        }
    }

    /** Riquadro compatto in alto a sinistra: largo come la zona (senza zona al massimo 256 px). */
    private fun dimensionaRiquadro(z: ZonaLed.Zona?, sl: Int, sa: Int) {
        if (!::riquadro.isInitialized) return
        val w = z?.larghezza ?: minOf(256, sl.coerceAtLeast(64))
        val maxA = z?.altezza ?: sa
        val px = android.util.TypedValue.COMPLEX_UNIT_PX
        val pad = (w / 20).coerceAtLeast(3)
        riquadro.setPadding(pad, pad, pad, pad)
        rqTitolo.setTextSize(px, w / 13f)
        // 6 caratteri monospazio (≈0,6 em l'uno) nel 90% della larghezza.
        rqCodice.setTextSize(px, minOf((w - 2 * pad) / 3.9f, maxA / 4f))
        rqCodice.setPadding(0, pad, 0, pad)
        rqStato.setTextSize(px, w / 17f)
        rqNota.setTextSize(px, w / 21f)
        rqNota.setPadding(0, pad / 2, 0, 0)
        riquadro.layoutParams = android.widget.FrameLayout.LayoutParams(w, -2, android.view.Gravity.TOP or android.view.Gravity.START)
        aggiornaNota()
    }

    private fun aggiornaNota() {
        val (sl, sa) = dimensioniSchermo()
        rqNota.text = "v${BuildConfig.VERSION_NAME} · " +
            (zonaApplicata?.let { "zona $it" } ?: "schermo intero ${sl}×${sa}")
    }

    private fun reteOk(): Boolean = runCatching {
        val cm = net.spotlive.player.piattaforma.servizio<android.net.ConnectivityManager>(this, CONNECTIVITY_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            // SVILUPPO-20261114-02: activeNetwork esiste da API 23.
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }.getOrDefault(false)

    /**
     * Ogni 2 s finché il player non è abbinato: legge il codice dalla pagina e lo mostra nel riquadro
     * compatto in alto a sinistra (dentro la zona LED), così si vede anche su un pannello di 256×384.
     */
    private fun controllaAbbinamento() {
        val tokenNativo = runCatching {
            (application as PlayerApplicazione).archivioSicuro.leggi(net.spotlive.player.piattaforma.ArchivioSicuro.CHIAVE_TOKEN).first
        }.getOrDefault(false)
        // 1.0.9: il token del player web resta nell'IndexedDB della WebView, quindi l'archivio nativo di solito
        // è vuoto anche da abbinato. Vale come abbinato anche l'ultima pagina vista senza codice (ricordata).
        val prefs = getSharedPreferences(PREF_ABB, MODE_PRIVATE)
        fun abbinato() = tokenNativo || prefs.getBoolean("pagina_abbinata", false)
        // Prima lo stato nativo (anche se la pagina non risponde), poi il codice letto dalla pagina.
        mostraRiquadro(codiceLetto, abbinato())
        runCatching {
            webView.evaluateJavascript(SCRIPT_CODICE) { v ->
                val letto = v?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                if (letto == PAGINA_ABBINATA) {
                    if (!prefs.getBoolean("pagina_abbinata", false)) {
                        prefs.edit().putBoolean("pagina_abbinata", true).apply()
                        net.spotlive.player.sistema.Diario.evento(this, "pagina abbinata: riquadro nascosto")
                    }
                    codiceLetto = null
                } else {
                    // Pagina in abbinamento (anche dopo una revoca): torna a mostrare il riquadro.
                    if (letto != null && prefs.getBoolean("pagina_abbinata", false)) {
                        prefs.edit().putBoolean("pagina_abbinata", false).apply()
                    }
                    codiceLetto = letto
                }
                mostraRiquadro(codiceLetto, abbinato())
            }
        }
        gestoreOrario.removeCallbacks(cicloAbbinamento)
        gestoreOrario.postDelayed(cicloAbbinamento, if (abbinato()) 30_000L else 2_000L)
    }

    private fun mostraRiquadro(codice: String?, abbinato: Boolean) {
        // Il codice è nella pagina solo durante l'abbinamento; senza pagina vale lo stato del token.
        val inAbbinamento = codice != null || !abbinato
        if (!inAbbinamento) {
            if (riquadro.visibility != android.view.View.GONE) riquadro.visibility = android.view.View.GONE
            ultimoCodice = null
            return
        }
        val valido = codice != null && codice.any { it != '-' }
        rqCodice.text = if (valido) codice else "······"
        val rete = reteOk()
        sondaServer()
        val base = when {
            !rete -> "Rete assente: controlla cavo/Wi-Fi"
            valido -> "Inserisci il codice nel pannello SpotLive. In attesa…"
            else -> "Collegamento al server…"
        }
        rqStato.text = if (valido) base else buildString {
            avvisoWebView?.let { append(it).append("\n") } // SVILUPPO-20261114-02, solo legacy
            append(base)
            erroreCaricamento?.let { append("\nErrore ").append(it) }
            append("\nOra dispositivo: ").append(Diagnosi.ora())
            append("\nURL: ").append(urlPlayer().take(80))
            append("\n").append(esitoSonda ?: "sonda: in corso…")
        }
        aggiornaNota()
        if (riquadro.visibility != android.view.View.VISIBLE) riquadro.visibility = android.view.View.VISIBLE
        if (valido && codice != ultimoCodice) {
            ultimoCodice = codice
            net.spotlive.player.sistema.Diario.evento(this, "codice di abbinamento mostrato nel riquadro (${zonaApplicata ?: "schermo intero"})")
        }
    }

    // ---------------- 1.0.12: striscia di diagnosi sul LED (mai il token) ----------------

    private lateinit var striscia: android.widget.TextView
    private val avvioMs = android.os.SystemClock.elapsedRealtime()
    @Volatile private var manifestNativo: String = "-"
    @Volatile private var passaggioVisto = false
    private val cicloDiagnosi = object : Runnable {
        override fun run() {
            aggiornaStriscia()
            gestoreOrario.postDelayed(this, 3_000L)
        }
    }

    private fun creaStriscia() {
        striscia = android.widget.TextView(this).apply {
            setTextColor(android.graphics.Color.rgb(235, 235, 235))
            setBackgroundColor(android.graphics.Color.argb(170, 0, 0, 0))
            typeface = android.graphics.Typeface.MONOSPACE
            includeFontPadding = false
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            visibility = android.view.View.GONE
        }
    }

    /** In basso dentro la zona LED (o in basso a sinistra a schermo intero), metà altezza al massimo. */
    private fun dimensionaStriscia(z: ZonaLed.Zona?, sl: Int, sa: Int) {
        if (!::striscia.isInitialized) return
        val w = z?.larghezza ?: minOf(sl, 640)
        val altZona = z?.altezza ?: sa
        val h = (altZona / 2).coerceAtLeast(40)
        val px = android.util.TypedValue.COMPLEX_UNIT_PX
        striscia.setTextSize(px, (w / 26f).coerceIn(8f, 18f))
        val pad = (w / 64).coerceAtLeast(2)
        striscia.setPadding(pad, pad, pad, pad)
        striscia.layoutParams = android.widget.FrameLayout.LayoutParams(w, h, android.view.Gravity.TOP or android.view.Gravity.START).apply {
            topMargin = altZona - h
        }
    }

    /** Script a inizio documento solo per l'origine del player (errori JS, WebSocket, esito manifest/passaggi). */
    private fun iniettaDiagnosiInizio() {
        runCatching {
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                DiagnosiLed.aggiungiErrore("diagnosi: script a inizio pagina non supportato da questa WebView")
                return
            }
            val u = android.net.Uri.parse(urlPlayer())
            val origine = "${u.scheme}://${u.host}"
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, DiagnosiLed.SCRIPT_INIZIO, setOf(origine))
        }.onFailure { DiagnosiLed.aggiungiErrore("diagnosi: iniezione fallita ${it.javaClass.simpleName}") }
    }

    private fun versioneWebView(): String = runCatching {
        val p = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(this)
        if (p == null) "?" else "${p.packageName.substringAfterLast('.')} ${p.versionName}"
    }.getOrDefault("?")

    private fun aggiornaStriscia() {
        if (!::striscia.isInitialized) return
        runCatching {
            webView.evaluateJavascript(DiagnosiLed.SCRIPT_STATO) { v ->
                runCatching { componiStriscia(v) }
            }
        }
    }

    private fun componiStriscia(v: String?) {
        if (isFinishing || isDestroyed) return
        var stato: org.json.JSONObject? = null
        runCatching {
            // evaluateJavascript restituisce la stringa JSON tra virgolette.
            val testo = org.json.JSONTokener(v ?: "null").nextValue() as? String
            if (testo != null) stato = org.json.JSONObject(testo)
        }
        val d = stato?.optJSONObject("d")
        if ((d?.optInt("pasOk") ?: 0) > 0) passaggioVisto = true
        val trascorsi = android.os.SystemClock.elapsedRealtime() - avvioMs
        val mostra = !passaggioVisto || trascorsi < DiagnosiLed.DURATA_MIN_MS
        if (!mostra) {
            if (striscia.visibility != android.view.View.GONE) {
                striscia.visibility = android.view.View.GONE
                net.spotlive.player.sistema.Diario.evento(this, "diagnosi LED nascosta (passaggio registrato, oltre 10 min)")
            }
            return
        }
        val ua = runCatching { webView.settings.userAgentString }.getOrNull()
        val righe = mutableListOf<String>()
        righe += "v${BuildConfig.VERSION_NAME} A${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT} WV ${versioneWebView()} Chr ${DiagnosiLed.chromeDaUa(ua)}"
        avvisoWebView?.let { righe += "! $it" } // SVILUPPO-20261114-02, solo legacy
        righe += "${Diagnosi.ora().substring(11, 19)} man pag ${d?.optString("man")?.ifBlank { "-" } ?: "?"} nat $manifestNativo cache ${if (stato?.optBoolean("cache") == true) "sì" else "no"}"
        righe += "live ${d?.optString("ws") ?: "?"} (${d?.optInt("wsN") ?: 0}x) SW ${if (stato?.optBoolean("sw") == true) "sì" else "no"} ${stato?.optString("vis") ?: ""}"
        righe += "pass ${d?.optString("pas")?.ifBlank { "-" } ?: "?"} ok ${d?.optInt("pasOk") ?: 0}"
        righe += "spot ${stato?.optString("spot") ?: (stato?.optString("x")?.let { "err $it" } ?: "pagina non risponde")}"
        val jsErr = mutableListOf<String>()
        d?.optJSONArray("err")?.let { a -> for (i in 0 until a.length()) jsErr += a.optString(i) }
        val tutti = (DiagnosiLed.errori() + jsErr.map { DiagnosiLed.oscura(it) }).takeLast(DiagnosiLed.MAX_ERRORI)
        if (tutti.isEmpty()) righe += "errori JS: nessuno" else tutti.forEach { righe += "! $it" }
        erroreCaricamento?.let { righe += "rete: ${DiagnosiLed.oscura(it).take(100)}" }
        striscia.text = righe.joinToString("\n")
        if (striscia.visibility != android.view.View.VISIBLE) striscia.visibility = android.view.View.VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) nascondiBarre()
    }

    /** Modalità immersiva: barre di stato e navigazione nascoste, riappaiono solo con uno swipe e poi si richiudono. */
    private fun nascondiBarre() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        // Un'istanza vecchia che si chiude non deve segnare "non visibile" il player nuovo.
        if (istanza?.get() === this || istanza?.get() == null) visibile = false
        if (!isFinishing) net.spotlive.player.sistema.Diario.evento(this, "player in pausa (coperto o schermo spento)")
        gestoreOrario.removeCallbacks(cicloOrario)
        gestoreOrario.removeCallbacks(cicloAbbinamento)
        gestoreOrario.removeCallbacks(cicloDiagnosi)
        super.onPause()
    }

    private fun urlPlayer(): String {
        if (BuildConfig.URL_PLAYER.startsWith("https://")) return BuildConfig.URL_PLAYER
        val assetIndex = "https://appassets.androidplatform.net/assets/player-web/index.html"
        val assetDemo = "https://appassets.androidplatform.net/assets/ponte-demo.html"
        return try {
            assets.open("player-web/index.html").close()
            assetIndex
        } catch (_: Exception) {
            assetDemo
        }
    }

    private fun injectPonteApi() {
        webView.evaluateJavascript(
            """
            (function() {
              if (window.SpotlivePonte) return;
              window.SpotlivePonte = {
                richiedi: function(richiesta) {
                  var payload = typeof richiesta === 'string' ? richiesta : JSON.stringify(richiesta);
                  return JSON.parse(SpotlivePonteNativo.richiedi(payload));
                }
              };
            })();
            """.trimIndent(),
            null,
        )
    }

    override fun onDestroy() {
        val app = application as PlayerApplicazione
        app.ponteGestore.scollega(this)
        if (istanza?.get() === this) istanza = null
        runCatching { webView.destroy() }
        super.onDestroy()
    }
}
