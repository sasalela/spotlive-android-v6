package net.spotlive.player.cache

import android.content.Context
import net.spotlive.player.ponte.AssetDaScaricare
import net.spotlive.player.ponte.RisultatoAssicura
import net.spotlive.player.ponte.RisultatoLibera
import net.spotlive.player.ponte.RisultatoSpazio
import net.spotlive.player.ponte.RisultatoUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CacheNativa(
    private val contesto: Context,
    client: OkHttpClient? = null,
) {
    private val directory: File =
        File(contesto.filesDir, "media-cache").also { it.mkdirs() }

    private val http: OkHttpClient =
        client
            ?: OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

    /** Quota indicativa per la risposta `spazio` (MB). */
    var quotaMb: Int = 2_000

    fun percorsoAsset(sha256: String): File = File(directory, sha256.lowercase())

    fun urlPerWebView(sha256: String): String = "spotlive-media://${sha256.lowercase()}"

    fun assicura(asset: List<AssetDaScaricare>): RisultatoAssicura {
        var byteScaricati = 0L
        var completati = 0
        for (elemento in asset) {
            val destinazione = percorsoAsset(elemento.sha256)
            if (destinazione.exists() && verificaHash(destinazione, elemento.sha256)) {
                completati++
                continue
            }
            val scaricati = scaricaConRipresa(elemento, destinazione)
            byteScaricati += scaricati
            if (!verificaHash(destinazione, elemento.sha256)) {
                destinazione.delete()
                throw IOException("hash non valido per ${elemento.sha256}")
            }
            completati++
        }
        return RisultatoAssicura(completati = completati, totale = asset.size, byteScaricati = byteScaricati)
    }

    fun url(sha256: String): RisultatoUrl {
        val file = percorsoAsset(sha256)
        if (!file.exists()) {
            throw IOException("asset assente in cache: $sha256")
        }
        return RisultatoUrl(url = urlPerWebView(sha256))
    }

    fun libera(daTenere: List<String>): RisultatoLibera {
        val tenere = daTenere.map { it.lowercase() }.toSet()
        var rimossi = 0
        directory.listFiles()?.forEach { file ->
            if (file.name !in tenere) {
                if (file.delete()) rimossi++
            }
        }
        return RisultatoLibera(rimossi = rimossi)
    }

    fun spazio(): RisultatoSpazio {
        val usatoByte = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val liberoByte = directory.freeSpace
        return RisultatoSpazio(
            liberoMb = (liberoByte / (1024 * 1024)).toInt(),
            usatoMb = (usatoByte / (1024 * 1024)).toInt(),
            quotaMb = quotaMb,
        )
    }

    fun apriStream(sha256: String): FileInputStream? {
        val file = percorsoAsset(sha256)
        return if (file.isFile) FileInputStream(file) else null
    }

    private fun scaricaConRipresa(asset: AssetDaScaricare, destinazione: File): Long {
        var scaricati = 0L
        var offset = if (destinazione.exists()) destinazione.length() else 0L
        if (offset > asset.byte) {
            destinazione.delete()
            offset = 0
        }
        val richiestaBuilder =
            Request.Builder().url(risolviUrl(asset.url)).header("Accept-Encoding", "identity")
        if (offset > 0) {
            richiestaBuilder.header("Range", "bytes=$offset-")
        }
        http.newCall(richiestaBuilder.build()).execute().use { risposta ->
            if (!risposta.isSuccessful && !(offset > 0 && risposta.code == 416)) {
                throw IOException("download fallito: ${risposta.code}")
            }
            val corpo = risposta.body ?: throw IOException("corpo vuoto")
            RandomAccessFile(destinazione, "rw").use { file ->
                file.seek(offset)
                val buffer = ByteArray(64 * 1024)
                corpo.byteStream().use { ingresso ->
                    while (true) {
                        val letti = ingresso.read(buffer)
                        if (letti <= 0) break
                        file.write(buffer, 0, letti)
                        scaricati += letti
                    }
                }
            }
        }
        return scaricati
    }

    private fun risolviUrl(percorso: String): String {
        if (percorso.startsWith("http://") || percorso.startsWith("https://")) return percorso
        // In sviluppo il player-web userà URL relativi verso il server; qui non abbiamo base URL.
        throw IOException("URL relativo non risolvibile nel guscio: $percorso")
    }

    companion object {
        fun verificaHash(file: File, sha256Atteso: String): Boolean {
            if (!file.isFile) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ingresso ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val letti = ingresso.read(buffer)
                    if (letti <= 0) break
                    digest.update(buffer, 0, letti)
                }
            }
            val calcolato = digest.digest().joinToString("") { "%02x".format(it) }
            return calcolato == sha256Atteso.lowercase()
        }

        fun verificaHashContenuto(contenuto: ByteArray, sha256Atteso: String): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(contenuto)
            val calcolato = digest.digest().joinToString("") { "%02x".format(it) }
            return calcolato == sha256Atteso.lowercase()
        }
    }
}
