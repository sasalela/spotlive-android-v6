package com.spotlive.player

/**
 * Configurazione centralizzata per SpotLive Player Android
 *
 * IMPORTANTE: Questi parametri sono hardcoded nel codice esattamente come nella versione Windows.
 * L'unico modo per modificarli è cambiare i valori qui e ricompilare l'APK.
 *
 * I parametri ID_SCHERMO e USER_SCHERMO vengono invece richiesti all'utente al primo avvio
 * e salvati nelle SharedPreferences del dispositivo.
 */
object Config {

    // ============================================================================
    // PARAMETRI SERVER - MODIFICABILI SOLO DA SVILUPPATORI
    // ============================================================================

    /**
     * URL base del server SpotLiveScreen
     *
     * Questo è l'endpoint principale che gestisce la programmazione XML.
     * Cambiare questo valore SOLO se il server viene spostato su un altro host/porta.
     *
     * FORMATO: http://IP:PORTA/PATH
     * ESEMPIO: http://80.88.90.214:80/spotlivescreen/XmlServlet
     */
    const val SERVER_URL = "http://80.88.90.214:80/spotlivescreen/XmlServlet"

    /**
     * Indirizzo IP/hostname del server
     * Usato per connessioni FTP e fallback
     */
    const val SERVER_HOST = "80.88.90.214"

    /**
     * Versione protocollo/client
     * Inviato al server per compatibilità versioni
     */
    const val VERSION = "511"

    // ============================================================================
    // PARAMETRI FTP - PER DOWNLOAD MEDIA
    // ============================================================================

    /**
     * Porta FTP standard
     */
    const val FTP_PORT = 21

    /**
     * Username FTP per autenticazione
     * Credenziali fornite dal server centrale
     */
    const val FTP_USERNAME = "01fcbgyvir"

    /**
     * Password FTP per autenticazione
     * Credenziali fornite dal server centrale
     */
    const val FTP_PASSWORD = "01fcbgyvir"

    /**
     * Directory base FTP da cui scaricare i media
     */
    const val FTP_DIRECTORY = "/"

    /**
     * Modalità connessione FTP
     * 3 = Passive Mode (PASV) - raccomandato per firewall/NAT
     * 1,2 = altre modalità (non documentate)
     */
    const val FTP_CONNECTION_MODE = 3

    // ============================================================================
    // PARAMETRI TIMING E PERFORMANCE
    // ============================================================================

    /**
     * Delay in millisecondi tra aggiornamenti playlist
     * Default: 20000ms = 20 secondi
     */
    const val DELAY_MS = 20000L

    /**
     * Secondi di cache per video
     */
    const val CACHE_SECONDS = 5

    /**
     * Secondi di tolleranza per sincronizzazione
     */
    const val TOLERANCE_SECONDS = 12

    /**
     * Qualità video (parametro legacy, potrebbe non essere usato)
     */
    const val VIDEO_QUALITY = 34

    // ============================================================================
    // PARAMETRI PROXY SOCKS5 (OPZIONALI)
    // ============================================================================

    /**
     * Abilita proxy SOCKS5
     * 0 = disabilitato, 1 = abilitato
     */
    const val PROXY_ENABLED = 0

    /**
     * Host proxy SOCKS5
     */
    const val PROXY_HOST = "10.30.254.2"

    /**
     * Porta proxy SOCKS5
     */
    const val PROXY_PORT = 1080

    /**
     * Username proxy (se richiesto)
     */
    const val PROXY_USERNAME = "LA09950"

    /**
     * Password proxy (se richiesto)
     */
    const val PROXY_PASSWORD = "LA09950"

    // ============================================================================
    // PARAMETRI LOCALI ANDROID
    // ============================================================================

    /**
     * Porta locale su cui gira il server Go embedded
     */
    const val LOCAL_SERVER_PORT = 8080

    /**
     * Nome file delle SharedPreferences per salvare configurazione utente
     */
    const val PREFS_NAME = "SpotLivePrefs"

    /**
     * Chiave per ID_SCHERMO nelle SharedPreferences
     */
    const val PREF_KEY_ID_SCHERMO = "id_schermo"

    /**
     * Chiave per USER_SCHERMO nelle SharedPreferences
     */
    const val PREF_KEY_USER_SCHERMO = "user_schermo"

    /**
     * Chiave per flag "configurato" nelle SharedPreferences
     */
    const val PREF_KEY_CONFIGURED = "is_configured"

    // ============================================================================
    // FUNZIONI UTILITY
    // ============================================================================

    /**
     * Costruisce l'URL completo per scaricare la programmazione XML
     *
     * @param idSchermo ID univoco dello schermo
     * @param userSchermo Username/identificativo utente dello schermo
     * @return URL completo con parametri query string
     */
    fun buildProgrammationUrl(idSchermo: String, userSchermo: String): String {
        return "$SERVER_URL?version=$VERSION&idSchermo=$idSchermo&userschermo=$userSchermo"
    }

    /**
     * Costruisce l'URL per inviare update heartbeat al server
     *
     * @param idSchermo ID univoco dello schermo
     * @param userSchermo Username/identificativo utente dello schermo
     * @return URL completo per heartbeat
     */
    fun buildUpdateUrl(idSchermo: String, userSchermo: String): String {
        return "$SERVER_URL?version=$VERSION&update=1&idSchermo=$idSchermo&userschermo=$userSchermo"
    }

    /**
     * Costruisce l'URL locale del server embedded
     *
     * @return URL localhost
     */
    fun buildLocalServerUrl(): String {
        return "http://localhost:$LOCAL_SERVER_PORT"
    }
}
