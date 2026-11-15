package net.spotlive.player.ponte

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val ponteJson =
    Json {
        ignoreUnknownKeys = false
        explicitNulls = true
        encodeDefaults = true
        isLenient = false
    }

@Serializable
data class AssetDaScaricare(
    val sha256: String,
    val url: String,
    val byte: Long,
)

@Serializable
data class RichiestaInfo(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    val metodo: String = "info",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RichiestaCapacita(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    val metodo: String = "capacita",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RichiestaIstantanea(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    val metodo: String = "istantanea",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RichiestaRiavviaApp(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    val metodo: String = "riavviaApp",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RichiestaRiavviaDispositivo(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    val metodo: String = "riavviaDispositivo",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ParametriArchivioLeggi(val chiave: String)

@Serializable
data class RichiestaArchivioLeggi(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.leggi",
    val parametri: ParametriArchivioLeggi,
)

@Serializable
data class ParametriArchivioScrivi(val chiave: String, val valore: String)

@Serializable
data class RichiestaArchivioScrivi(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.scrivi",
    val parametri: ParametriArchivioScrivi,
)

@Serializable
data class ParametriArchivioCancella(val chiave: String)

@Serializable
data class RichiestaArchivioCancella(
    val v: Int = 1,
    val id: String,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.cancella",
    val parametri: ParametriArchivioCancella,
)

@Serializable
data class ParametriAssicura(val asset: List<AssetDaScaricare>)

@Serializable
data class RichiestaAssicura(
    val v: Int = 1,
    val id: String,
    val porta: String = "cache",
    val metodo: String = "assicura",
    val parametri: ParametriAssicura,
)

@Serializable
data class ParametriUrl(val sha256: String)

@Serializable
data class RichiestaUrl(
    val v: Int = 1,
    val id: String,
    val porta: String = "cache",
    val metodo: String = "url",
    val parametri: ParametriUrl,
)

@Serializable
data class ParametriLibera(val daTenere: List<String>)

@Serializable
data class RichiestaLibera(
    val v: Int = 1,
    val id: String,
    val porta: String = "cache",
    val metodo: String = "libera",
    val parametri: ParametriLibera,
)

@Serializable
data class RichiestaSpazio(
    val v: Int = 1,
    val id: String,
    val porta: String = "cache",
    val metodo: String = "spazio",
    val parametri: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RisultatoInfo(
    val piattaforma: String,
    val modello: String,
    val versionePlayer: String,
)

@Serializable
data class RispostaInfo(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    val metodo: String = "info",
    val risultato: RisultatoInfo,
)

@Serializable
data class RisultatoCapacita(val capacita: List<String>)

@Serializable
data class RispostaCapacita(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    val metodo: String = "capacita",
    val risultato: RisultatoCapacita,
)

@Serializable
data class RisultatoIstantanea(val mime: String, val base64: String)

@Serializable
data class RispostaIstantanea(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    val metodo: String = "istantanea",
    val risultato: RisultatoIstantanea,
)

@Serializable
data class RisultatoAccettato(val accettato: Boolean = true)

@Serializable
data class RispostaRiavviaApp(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    val metodo: String = "riavviaApp",
    val risultato: RisultatoAccettato,
)

@Serializable
data class RispostaRiavviaDispositivo(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    val metodo: String = "riavviaDispositivo",
    val risultato: RisultatoAccettato,
)

@Serializable
data class RisultatoArchivioLeggi(val presente: Boolean, val valore: String?)

@Serializable
data class RispostaArchivioLeggi(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.leggi",
    val risultato: RisultatoArchivioLeggi,
)

@Serializable
data class RisultatoScritto(val scritto: Boolean = true)

@Serializable
data class RispostaArchivioScrivi(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.scrivi",
    val risultato: RisultatoScritto,
)

@Serializable
data class RisultatoCancellato(val cancellato: Boolean = true)

@Serializable
data class RispostaArchivioCancella(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "piattaforma",
    @SerialName("metodo") val metodo: String = "archivioSicuro.cancella",
    val risultato: RisultatoCancellato,
)

@Serializable
data class RisultatoAssicura(
    val completati: Int,
    val totale: Int,
    val byteScaricati: Long,
)

@Serializable
data class RispostaAssicura(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "cache",
    val metodo: String = "assicura",
    val risultato: RisultatoAssicura,
)

@Serializable
data class RisultatoUrl(val url: String)

@Serializable
data class RispostaUrl(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "cache",
    val metodo: String = "url",
    val risultato: RisultatoUrl,
)

@Serializable
data class RisultatoLibera(val rimossi: Int)

@Serializable
data class RispostaLibera(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "cache",
    val metodo: String = "libera",
    val risultato: RisultatoLibera,
)

@Serializable
data class RisultatoSpazio(val liberoMb: Int, val usatoMb: Int, val quotaMb: Int)

@Serializable
data class RispostaSpazio(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = true,
    val porta: String = "cache",
    val metodo: String = "spazio",
    val risultato: RisultatoSpazio,
)

@Serializable
data class DettaglioErrorePonte(val code: String, val message: String)

@Serializable
data class RispostaErrorePonte(
    val v: Int = 1,
    val id: String,
    val ok: Boolean = false,
    val porta: String,
    val metodo: String,
    val errore: DettaglioErrorePonte,
)

fun metodoRichiesta(json: String): String {
    val root = ponteJson.parseToJsonElement(json).jsonObject
    return root["metodo"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("metodo mancante")
}

fun portaRichiesta(json: String): String {
    val root = ponteJson.parseToJsonElement(json).jsonObject
    return root["porta"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("porta mancante")
}

fun idRichiesta(json: String): String {
    val root = ponteJson.parseToJsonElement(json).jsonObject
    return root["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("id mancante")
}

/** Decodifica una richiesta in base al metodo (per i test di contratto). */
fun decodificaRichiesta(json: String): JsonElement = ponteJson.parseToJsonElement(json)

fun codificaRisposta(elemento: JsonElement): String = ponteJson.encodeToString(JsonElement.serializer(), elemento)

fun codificaRispostaDaOggetto(risposta: Any): String =
    when (risposta) {
        is RispostaInfo -> ponteJson.encodeToString(RispostaInfo.serializer(), risposta)
        is RispostaCapacita -> ponteJson.encodeToString(RispostaCapacita.serializer(), risposta)
        is RispostaIstantanea -> ponteJson.encodeToString(RispostaIstantanea.serializer(), risposta)
        is RispostaRiavviaApp -> ponteJson.encodeToString(RispostaRiavviaApp.serializer(), risposta)
        is RispostaRiavviaDispositivo ->
            ponteJson.encodeToString(RispostaRiavviaDispositivo.serializer(), risposta)
        is RispostaArchivioLeggi -> ponteJson.encodeToString(RispostaArchivioLeggi.serializer(), risposta)
        is RispostaArchivioScrivi -> ponteJson.encodeToString(RispostaArchivioScrivi.serializer(), risposta)
        is RispostaArchivioCancella ->
            ponteJson.encodeToString(RispostaArchivioCancella.serializer(), risposta)
        is RispostaAssicura -> ponteJson.encodeToString(RispostaAssicura.serializer(), risposta)
        is RispostaUrl -> ponteJson.encodeToString(RispostaUrl.serializer(), risposta)
        is RispostaLibera -> ponteJson.encodeToString(RispostaLibera.serializer(), risposta)
        is RispostaSpazio -> ponteJson.encodeToString(RispostaSpazio.serializer(), risposta)
        is RispostaErrorePonte -> ponteJson.encodeToString(RispostaErrorePonte.serializer(), risposta)
        else -> throw IllegalArgumentException("tipo risposta sconosciuto")
    }

fun codificaRichiesta(oggetto: Any): String =
    when (oggetto) {
        is RichiestaInfo -> ponteJson.encodeToString(RichiestaInfo.serializer(), oggetto)
        is RichiestaCapacita -> ponteJson.encodeToString(RichiestaCapacita.serializer(), oggetto)
        is RichiestaIstantanea -> ponteJson.encodeToString(RichiestaIstantanea.serializer(), oggetto)
        is RichiestaRiavviaApp -> ponteJson.encodeToString(RichiestaRiavviaApp.serializer(), oggetto)
        is RichiestaRiavviaDispositivo ->
            ponteJson.encodeToString(RichiestaRiavviaDispositivo.serializer(), oggetto)
        is RichiestaArchivioLeggi -> ponteJson.encodeToString(RichiestaArchivioLeggi.serializer(), oggetto)
        is RichiestaArchivioScrivi -> ponteJson.encodeToString(RichiestaArchivioScrivi.serializer(), oggetto)
        is RichiestaArchivioCancella ->
            ponteJson.encodeToString(RichiestaArchivioCancella.serializer(), oggetto)
        is RichiestaAssicura -> ponteJson.encodeToString(RichiestaAssicura.serializer(), oggetto)
        is RichiestaUrl -> ponteJson.encodeToString(RichiestaUrl.serializer(), oggetto)
        is RichiestaLibera -> ponteJson.encodeToString(RichiestaLibera.serializer(), oggetto)
        is RichiestaSpazio -> ponteJson.encodeToString(RichiestaSpazio.serializer(), oggetto)
        else -> throw IllegalArgumentException("tipo richiesta sconosciuto")
    }

fun decodificaRichiestaTipizzata(json: String): Any =
    when (metodoRichiesta(json)) {
        "info" -> ponteJson.decodeFromString(RichiestaInfo.serializer(), json)
        "capacita" -> ponteJson.decodeFromString(RichiestaCapacita.serializer(), json)
        "istantanea" -> ponteJson.decodeFromString(RichiestaIstantanea.serializer(), json)
        "riavviaApp" -> ponteJson.decodeFromString(RichiestaRiavviaApp.serializer(), json)
        "riavviaDispositivo" -> ponteJson.decodeFromString(RichiestaRiavviaDispositivo.serializer(), json)
        "archivioSicuro.leggi" -> ponteJson.decodeFromString(RichiestaArchivioLeggi.serializer(), json)
        "archivioSicuro.scrivi" -> ponteJson.decodeFromString(RichiestaArchivioScrivi.serializer(), json)
        "archivioSicuro.cancella" -> ponteJson.decodeFromString(RichiestaArchivioCancella.serializer(), json)
        "assicura" -> ponteJson.decodeFromString(RichiestaAssicura.serializer(), json)
        "url" -> ponteJson.decodeFromString(RichiestaUrl.serializer(), json)
        "libera" -> ponteJson.decodeFromString(RichiestaLibera.serializer(), json)
        "spazio" -> ponteJson.decodeFromString(RichiestaSpazio.serializer(), json)
        else -> throw IllegalArgumentException("metodo non supportato")
    }

fun decodificaRispostaTipizzata(json: String): Any {
    val root = ponteJson.parseToJsonElement(json).jsonObject
    if (root["ok"]?.jsonPrimitive?.booleanOrNull == false) {
        return ponteJson.decodeFromString(RispostaErrorePonte.serializer(), json)
    }
    return when (root["metodo"]?.jsonPrimitive?.content) {
        "info" -> ponteJson.decodeFromString(RispostaInfo.serializer(), json)
        "capacita" -> ponteJson.decodeFromString(RispostaCapacita.serializer(), json)
        "istantanea" -> ponteJson.decodeFromString(RispostaIstantanea.serializer(), json)
        "riavviaApp" -> ponteJson.decodeFromString(RispostaRiavviaApp.serializer(), json)
        "riavviaDispositivo" -> ponteJson.decodeFromString(RispostaRiavviaDispositivo.serializer(), json)
        "archivioSicuro.leggi" -> ponteJson.decodeFromString(RispostaArchivioLeggi.serializer(), json)
        "archivioSicuro.scrivi" -> ponteJson.decodeFromString(RispostaArchivioScrivi.serializer(), json)
        "archivioSicuro.cancella" -> ponteJson.decodeFromString(RispostaArchivioCancella.serializer(), json)
        "assicura" -> ponteJson.decodeFromString(RispostaAssicura.serializer(), json)
        "url" -> ponteJson.decodeFromString(RispostaUrl.serializer(), json)
        "libera" -> ponteJson.decodeFromString(RispostaLibera.serializer(), json)
        "spazio" -> ponteJson.decodeFromString(RispostaSpazio.serializer(), json)
        else -> throw IllegalArgumentException("metodo risposta sconosciuto")
    }
}
