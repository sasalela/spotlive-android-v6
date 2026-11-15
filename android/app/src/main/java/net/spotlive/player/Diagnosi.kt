package net.spotlive.player

/** 1.0.10: testi leggibili per la diagnosi di collegamento nel riquadro (nessun token, nessun segreto). */
object Diagnosi {
    fun ora(): String =
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss zzz", java.util.Locale.ITALY).format(java.util.Date())

    fun erroreSsl(primario: Int): String = when (primario) {
        android.net.http.SslError.SSL_NOTYETVALID -> "non ancora valido (data/ora del dispositivo indietro?)"
        android.net.http.SslError.SSL_EXPIRED -> "scaduto (data/ora del dispositivo avanti?)"
        android.net.http.SslError.SSL_IDMISMATCH -> "nome host non corrispondente"
        android.net.http.SslError.SSL_UNTRUSTED -> "autorità non riconosciuta (CA mancanti o rete che intercetta)"
        android.net.http.SslError.SSL_DATE_INVALID -> "data non valida (controlla data/ora)"
        else -> "non valido (codice $primario)"
    }

    fun erroreWebView(codice: Int): String = when (codice) {
        android.webkit.WebViewClient.ERROR_HOST_LOOKUP -> "DNS: host non trovato"
        android.webkit.WebViewClient.ERROR_CONNECT -> "server non raggiungibile"
        android.webkit.WebViewClient.ERROR_TIMEOUT -> "tempo scaduto"
        android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "errore TLS/certificato"
        android.webkit.WebViewClient.ERROR_IO -> "errore di rete in lettura/scrittura"
        android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION -> "proxy richiede autenticazione"
        android.webkit.WebViewClient.ERROR_BAD_URL -> "URL non valido"
        android.webkit.WebViewClient.ERROR_UNSAFE_RESOURCE -> "risorsa bloccata (Safe Browsing)"
        0 -> "errore"
        else -> "errore WebView $codice"
    }

    /** SVILUPPO-20261114-02: SpotLivePlayer/1.0.15-legacy (Android 5.1; API 22; WebView <pacchetto> <versione>). */
    fun userAgentLegacy(): String {
        val wv = net.spotlive.player.piattaforma.Variante.webView
        val webView = if (wv == null) "WebView ?" else "WebView ${wv["pacchetto"]} ${wv["versione"]}"
        return "SpotLivePlayer/${BuildConfig.VERSIONE_PLAYER} (Android ${android.os.Build.VERSION.RELEASE}; " +
            "API ${android.os.Build.VERSION.SDK_INT}; $webView)"
    }

    /** GET senza credenziali: codice HTTP o tipo di errore. Per l'API senza token 401 = server raggiungibile. */
    fun prova(url: String): String = try {
        val c = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        // SVILUPPO-20261114-02: nella legacy fiducia CA di sistema + ISRG (standard: null, nessun cambio).
        net.spotlive.player.piattaforma.Tls.socketFactory()?.let { c.sslSocketFactory = it }
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "*/*")
            // Solo legacy: la versione della WebView resta nel log di accesso del server (se il proxy lo registra).
            if (net.spotlive.player.piattaforma.Variante.legacy) c.setRequestProperty("User-Agent", userAgentLegacy())
            val code = c.responseCode
            when {
                code == 401 && url.contains("/api/") -> "OK (401 senza token)"
                code in 200..399 -> "OK ($code)"
                else -> "HTTP $code"
            }
        } finally {
            c.disconnect()
        }
    } catch (e: java.net.UnknownHostException) {
        "DNS: host non trovato"
    } catch (e: java.net.SocketTimeoutException) {
        "tempo scaduto"
    } catch (e: java.net.ConnectException) {
        "connessione rifiutata/irraggiungibile"
    } catch (e: javax.net.ssl.SSLHandshakeException) {
        val m = (e.cause?.message ?: e.message ?: "").lowercase()
        when {
            "notyetvalid" in m || "not yet valid" in m -> "certificato non ancora valido (data/ora indietro)"
            "expired" in m -> "certificato scaduto (data/ora avanti)"
            "trust anchor" in m -> "certificato non riconosciuto (CA/data)"
            else -> "TLS: ${(e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName)}"
        }
    } catch (e: javax.net.ssl.SSLException) {
        "TLS: ${e.javaClass.simpleName}"
    } catch (e: Exception) {
        "errore ${e.javaClass.simpleName}"
    }
}
