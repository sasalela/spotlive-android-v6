package net.spotlive.player.aggiornamenti

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Versione da installare, da qualunque fonte (server per dispositivo, file del canale, file storico). */
data class IstruzioneAndroid(
    val canale: String,
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val certSha256: String?,
    val automatico: Boolean,
    val fonte: String,
)

enum class Decisione { NON_NECESSARIO, NON_AUTOMATICO, RITORNO_NON_POSSIBILE, ATTENDI_FUORI_ORARIO, ATTESA_TECNICO, INSTALLA }

/** Regole pure dell'aggiornamento Android (provate in RegolaAggiornamentoTest, senza dispositivo). */
object RegolaAggiornamento {
    /** Certificato di firma SpotLive (CN=SpotLive, O=Live Italia S.r.l.): un APK firmato da altri non si installa mai. */
    const val CERT_SPOTLIVE = "0814aaefb16436daa2754aade7175ac80e556f264587ecf9b98cd8dce03a7288"
    const val PACCHETTO = "net.spotlive.player"
    val CANALI = setOf("stabile", "prova")

    /**
     * JSON dell'istruzione: stesso formato per la risposta del server e per i file del canale;
     * il file storico versione-android.json è un sottoinsieme (niente canale né certificato).
     * `base` = indirizzo del documento letto, per risolvere un url relativo. Solo https.
     */
    fun leggi(o: JsonObject, base: String, fonte: String, canalePredefinito: String = "stabile"): IstruzioneAndroid? {
        fun s(k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val code = runCatching { o["versionCode"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: return null
        val rel = s("url") ?: s("urlApk") ?: return null
        val url = runCatching { java.net.URI(base).resolve(rel).toString() }.getOrNull() ?: return null
        if (!url.startsWith("https://")) return null
        val sha = (s("sha256") ?: "").lowercase()
        if (!Regex("^[0-9a-f]{64}$").matches(sha)) return null
        val cert = s("certSha256")?.lowercase()?.replace(":", "")?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
        val canale = s("canale")?.takeIf { it in CANALI } ?: canalePredefinito
        return IstruzioneAndroid(
            canale = canale,
            versionCode = code,
            versionName = s("versionName") ?: s("versione") ?: "?",
            url = url,
            sha256 = sha,
            certSha256 = cert,
            automatico = runCatching { o["automatico"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false,
            fonte = fonte,
        )
    }

    /**
     * @param silenzioso true se l'installazione non chiede tocchi (proprietario del dispositivo, o Android 12+ e
     *        SpotLive installatore della versione attuale)
     * @param orarioNoto/accesoOra dagli orari di accensione dello schermo
     * @param inAttesaDaMs da quanto la versione aspetta la chiusura (dopo 24 h si installa comunque, di notte)
     */
    fun decidi(
        i: IstruzioneAndroid,
        codiceAttuale: Int,
        forzato: Boolean,
        silenzioso: Boolean,
        orarioNoto: Boolean,
        accesoOra: Boolean,
        inAttesaDaMs: Long,
        oraLocale: Int,
    ): Decisione {
        if (i.versionCode == codiceAttuale) return Decisione.NON_NECESSARIO
        // Android non installa mai un versionCode più basso (nemmeno da proprietario): il ritorno indietro
        // si fa ripubblicando il codice precedente con un versionCode più alto.
        if (i.versionCode < codiceAttuale) {
            // Il file storico resta fermo a versioni vecchie: per lui non è un ritorno chiesto, è solo vecchio.
            return if (i.fonte == "storico") Decisione.NON_NECESSARIO else Decisione.RITORNO_NON_POSSIBILE
        }
        if (!forzato && !i.automatico) return Decisione.NON_AUTOMATICO
        // Senza installazione silenziosa la finestra "Aggiorna" coprirebbe la pubblicità del cliente:
        // si aspetta il tecnico (comando "Aggiorna player" dal pannello) e lo si segnala nello stato.
        if (!silenzioso && !forzato) return Decisione.ATTESA_TECNICO
        if (forzato) return Decisione.INSTALLA
        // Silenzioso: meglio a schermo chiuso. Schermo sempre acceso: dopo 24 h di attesa, tra le 3 e le 5.
        if (!orarioNoto || !accesoOra) return Decisione.INSTALLA
        if (inAttesaDaMs >= 24 * 3_600_000L && oraLocale in 3..4) return Decisione.INSTALLA
        return Decisione.ATTENDI_FUORI_ORARIO
    }

    /**
     * Firma dell'APK scaricato: deve coincidere con quella dell'app installata, contenere il certificato SpotLive
     * e, se l'istruzione lo indica, il certificato atteso. Nomi di pacchetto diversi: rifiuto.
     */
    fun firmaValida(pacchettoApk: String?, certApk: Set<String>, certInstallata: Set<String>, certAtteso: String?): Boolean {
        if (pacchettoApk != PACCHETTO) return false
        if (certApk.isEmpty() || certApk != certInstallata) return false
        if (CERT_SPOTLIVE !in certApk) return false
        if (certAtteso != null && certAtteso !in certApk) return false
        return true
    }
}
