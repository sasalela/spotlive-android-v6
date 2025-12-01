package com.spotlive.player

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefsManager: PreferencesManager
    private var serverProcess: Process? = null
    private var settingsButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inizializza PreferencesManager
        prefsManager = PreferencesManager(this)

        // Log configurazione per debug
        android.util.Log.d("SpotLive", prefsManager.getConfigurationSummary())

        // Verifica se è già configurato
        if (!prefsManager.isConfigured()) {
            // Prima configurazione richiesta
            android.util.Log.d("SpotLive", "App non configurata, mostro schermata configurazione")
            showConfigurationScreen()
            return
        }

        // App configurata, procedi normalmente
        android.util.Log.d("SpotLive", "App già configurata, avvio normale")

        // Enable fullscreen immersive mode (kiosk)
        enableKioskMode()

        // Start embedded Go server (OPZIONALE - commentato per prima versione test)
        // startEmbeddedServer()

        // Setup WebView
        setupWebView()

        // Give server time to start (o carica direttamente se no embedded server)
        webView.postDelayed({
            loadApp()
        }, 2000)
    }

    /**
     * Mostra la schermata di configurazione
     */
    private fun showConfigurationScreen() {
        val intent = Intent(this, ConfigurationActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun enableKioskMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupWebView() {
        // Container per WebView + pulsante impostazioni
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        // Pulsante impostazioni (piccolo, in alto a destra)
        // NOTA: In produzione, nascondilo o metti una gesture segreta
        settingsButton = Button(this).apply {
            text = "⚙"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setBackgroundColor(0x33333333)
            setPadding(dpToPx(8))
            alpha = 0.3f // Semi-trasparente per non disturbare
            setOnClickListener {
                openSettings()
            }
        }

        val btnParams = LinearLayout.LayoutParams(
            dpToPx(48),
            dpToPx(48)
        ).apply {
            gravity = Gravity.END
            setMargins(0, dpToPx(8), dpToPx(8), 0)
        }

        container.addView(settingsButton, btnParams)

        // WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Enable video playback
                mediaPlaybackRequiresUserGesture = false

                // Performance
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.d("SpotLive", "Page loaded: $url")
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    android.util.Log.e("SpotLive", "WebView error: $description")
                    // Retry after 5 seconds
                    view?.postDelayed({
                        loadApp()
                    }, 5000)
                }
            }

            webChromeClient = WebChromeClient()
        }

        val webViewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            weight = 1f
        }

        container.addView(webView, webViewParams)

        setContentView(container)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun openSettings() {
        android.util.Log.d("SpotLive", "Apertura schermata impostazioni")
        val intent = Intent(this, ConfigurationActivity::class.java)
        startActivity(intent)
    }

    private fun startEmbeddedServer() {
        try {
            // Binary path in assets
            val assetBinary = "spotlive-server"

            // Copy to internal storage
            val serverBinary = File(filesDir, "spotlive-server")

            if (!serverBinary.exists()) {
                android.util.Log.d("SpotLive", "Copying server binary from assets...")
                assets.open(assetBinary).use { input ->
                    serverBinary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                serverBinary.setExecutable(true)
                android.util.Log.d("SpotLive", "Server binary copied and made executable")
            }

            // Data directory
            val dataDir = filesDir.absolutePath

            // Pass configuration to server via arguments
            val idSchermo = prefsManager.getIdSchermo() ?: ""
            val userSchermo = prefsManager.getUserSchermo() ?: ""

            // Start server process
            android.util.Log.d("SpotLive", "Starting Go server...")
            val processBuilder = ProcessBuilder(
                serverBinary.absolutePath,
                "--port", Config.LOCAL_SERVER_PORT.toString(),
                "--data", dataDir,
                "--id-schermo", idSchermo,
                "--user-schermo", userSchermo
            )

            // Redirect errors to logcat
            processBuilder.redirectErrorStream(true)

            serverProcess = processBuilder.start()

            // Read output in background thread
            Thread {
                try {
                    serverProcess?.inputStream?.bufferedReader()?.use { reader ->
                        reader.forEachLine { line ->
                            android.util.Log.d("SpotLive-Server", line)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SpotLive", "Error reading server output", e)
                }
            }.start()

            android.util.Log.d("SpotLive", "Server started on port ${Config.LOCAL_SERVER_PORT}")

        } catch (e: Exception) {
            android.util.Log.e("SpotLive", "Failed to start server", e)
        }
    }

    private fun loadApp() {
        // Per prima versione di test, carichiamo una pagina di test
        // che mostra la configurazione
        val testHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SpotLive Player - Test</title>
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                    }
                    h1 { font-size: 3em; margin: 20px 0; }
                    .config-box {
                        background: rgba(255,255,255,0.1);
                        padding: 30px;
                        border-radius: 15px;
                        backdrop-filter: blur(10px);
                        max-width: 600px;
                        margin: 20px;
                    }
                    .config-item {
                        margin: 15px 0;
                        padding: 15px;
                        background: rgba(255,255,255,0.1);
                        border-radius: 8px;
                    }
                    .label {
                        font-size: 0.9em;
                        opacity: 0.8;
                        margin-bottom: 5px;
                    }
                    .value {
                        font-size: 1.3em;
                        font-weight: bold;
                    }
                    .status {
                        display: inline-block;
                        padding: 5px 15px;
                        background: #4CAF50;
                        border-radius: 20px;
                        font-size: 0.9em;
                    }
                </style>
            </head>
            <body>
                <h1>✓ SpotLive Player</h1>
                <div class="status">CONFIGURATO E PRONTO</div>

                <div class="config-box">
                    <h2>Configurazione Corrente</h2>

                    <div class="config-item">
                        <div class="label">ID Schermo</div>
                        <div class="value">${prefsManager.getIdSchermo() ?: "N/A"}</div>
                    </div>

                    <div class="config-item">
                        <div class="label">User Schermo</div>
                        <div class="value">${prefsManager.getUserSchermo() ?: "N/A"}</div>
                    </div>

                    <div class="config-item">
                        <div class="label">Server</div>
                        <div class="value">${Config.SERVER_HOST}</div>
                    </div>

                    <div class="config-item">
                        <div class="label">URL Programmazione</div>
                        <div class="value" style="font-size: 0.9em; word-break: break-all;">
                            ${prefsManager.getProgrammationUrl() ?: "N/A"}
                        </div>
                    </div>

                    <div class="config-item">
                        <div class="label">Versione</div>
                        <div class="value">${Config.VERSION}</div>
                    </div>
                </div>

                <p style="margin-top: 30px; opacity: 0.7; font-size: 0.9em;">
                    L'app è configurata correttamente e pronta per connettersi al server.<br>
                    Clicca il pulsante ⚙ in alto a destra per modificare la configurazione.
                </p>
            </body>
            </html>
        """.trimIndent()

        android.util.Log.d("SpotLive", "Loading test page")
        webView.loadDataWithBaseURL(null, testHtml, "text/html", "UTF-8", null)

        // TODO: In futuro, quando il backend Go è pronto, usa:
        // val url = Config.buildLocalServerUrl()
        // webView.loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop server
        serverProcess?.destroy()
        android.util.Log.d("SpotLive", "Server process destroyed")
    }

    override fun onBackPressed() {
        // Disable back button in kiosk mode
        // Uncomment for development:
        // super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableKioskMode()
        }
    }
}
