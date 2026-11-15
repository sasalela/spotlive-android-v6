package net.spotlive.player.sistema

/**
 * Regole pure (senza Context) della pausa manutenzione / modalità assistenza a tempo.
 * La pausa è la stessa di [Configurazione.pausa]: nessun secondo flag.
 */
object PausaAssistenza {
    /** Durate ammesse dal ponte "assistenza", in minuti. */
    val MINUTI_AMMESSI = setOf(30, 60)

    /** Oltre questo residuo il valore è sporco (es. rimasto da prima di un riavvio) e si ignora. */
    const val MASSIMO_MS = 2 * 60 * 60_000L

    /** Soglia odierna del watchdog: riapre il player dopo 2 controlli mancati (~20 s). */
    const val CONTROLLI_PRIMA_DI_RIAPRIRE = 2

    fun durataValida(minuti: Int): Boolean = minuti in MINUTI_AMMESSI

    /** Millisecondi di pausa restanti: 0 se scaduta o se il residuo è >= 2 ore. */
    fun restanteMs(finoElapsed: Long, oraElapsed: Long): Long =
        if (finoElapsed > oraElapsed && finoElapsed - oraElapsed < MASSIMO_MS) finoElapsed - oraElapsed else 0

    /** Con una pausa attiva il watchdog non interviene. */
    fun watchdogDeveSaltare(restanteMs: Long): Boolean = restanteMs > 0

    /**
     * Regola odierna del watchdog (documentata e fissata dai test): se il player è visibile o c'è
     * una pausa non si riapre nulla; fuori pausa si riapre dopo 2 controlli mancati.
     */
    fun watchdogDeveIntervenireDopoMancati(mancati: Int, restanteMs: Long, visibile: Boolean): Boolean =
        !visibile && !watchdogDeveSaltare(restanteMs) && mancati >= CONTROLLI_PRIMA_DI_RIAPRIRE
}
