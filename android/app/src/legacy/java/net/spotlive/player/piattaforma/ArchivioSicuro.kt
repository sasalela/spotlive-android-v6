// SVILUPPO-20261114-02: archivio della variante legacy (Android 5.1: niente chiavi AES in Android Keystore).
package net.spotlive.player.piattaforma

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/**
 * Archivio del token e della password del terminale, stessa API pubblica e stessi nomi di chiave della standard.
 * - Una coppia RSA-2048 in AndroidKeyStore (KeyPairGeneratorSpec, API 18) cifra una chiave AES-256 casuale;
 *   i valori si cifrano con AES/GCM/NoPadding nel file prefs `spotlive_archivio_legacy`.
 * - Se il Keystore non funziona: valori in chiaro nello stesso file privato (MODE_PRIVATE, allowBackup=false),
 *   evento nel Diario e `archivioCifrato=false` nello stato. Mai un crash all'avvio.
 */
class ArchivioSicuro internal constructor(contesto: Context, fornitore: (SharedPreferences) -> FornitoreChiave) {
    constructor(contesto: Context) : this(contesto, { p -> FornitoreChiaveKeystore(contesto, p) })

    private val prefs: SharedPreferences = contesto.getSharedPreferences(NOME_FILE, Context.MODE_PRIVATE)
    private val chiaveAes: SecretKey?

    init {
        chiaveAes = runCatching { fornitore(prefs).chiave() }
            .onFailure { Log.w(TAG, "Keystore non disponibile: archivio in chiaro", it) }
            .getOrNull()
        Variante.archivioCifrato = chiaveAes != null
        if (chiaveAes == null) {
            runCatching {
                net.spotlive.player.sistema.Diario.evento(contesto, "archivio: Keystore non disponibile, valori in chiaro (solo sandbox dell'app)")
            }
        }
    }

    internal val cifrato: Boolean get() = chiaveAes != null

    fun leggi(chiave: String): Pair<Boolean, String?> {
        if (chiave != CHIAVE_TOKEN) return Pair(false, null)
        val valore = valore(CHIAVE_TOKEN)
        return Pair(valore != null, valore)
    }

    fun scrivi(chiave: String, valore: String) {
        if (chiave != CHIAVE_TOKEN) throw IllegalArgumentException("chiave non supportata")
        prefs.edit().putString(CHIAVE_TOKEN, CifraturaArchivio.codifica(chiaveAes, CHIAVE_TOKEN, valore)).apply()
    }

    fun cancella(chiave: String) {
        if (chiave != CHIAVE_TOKEN) throw IllegalArgumentException("chiave non supportata")
        prefs.edit().remove(CHIAVE_TOKEN).apply()
    }

    // Come nella standard: solo per il codice nativo, mai in log, stato o telemetria.
    fun passwordTerminale(): String? = valore(CHIAVE_PASSWORD_TERMINALE)

    fun salvaPasswordTerminale(valore: String) {
        require(valore.isNotEmpty() && valore.length <= 128) { "password non valida" }
        prefs.edit().putString(CHIAVE_PASSWORD_TERMINALE, CifraturaArchivio.codifica(chiaveAes, CHIAVE_PASSWORD_TERMINALE, valore)).commit()
    }

    fun cancellaPasswordTerminale() {
        prefs.edit().remove(CHIAVE_PASSWORD_TERMINALE).commit()
    }

    private fun valore(chiave: String): String? = CifraturaArchivio.decifra(chiaveAes, chiave, prefs.getString(chiave, null))

    companion object {
        const val CHIAVE_TOKEN = "token"
        private const val CHIAVE_PASSWORD_TERMINALE = "taurus_password_terminale"
        internal const val NOME_FILE = "spotlive_archivio_legacy"
        private const val TAG = "SpotLiveArchivio"
    }
}

/** Fornisce la chiave AES dei valori; sostituibile nei test (Robolectric non ha AndroidKeyStore). */
fun interface FornitoreChiave {
    fun chiave(): SecretKey
}

/** Funzioni pure di cifratura dei valori (provate in ArchivioLegacyTest). */
object CifraturaArchivio {
    const val CIFRATO = "c1:"
    const val CHIARO = "p1:"
    private const val IV_BYTE = 12
    private const val TAG_BIT = 128

    /** Con chiave: "c1:" + hex(iv ‖ testo cifrato GCM), legato al nome della chiave (AAD). Senza: "p1:" + testo. */
    fun codifica(k: SecretKey?, nome: String, testo: String, rnd: SecureRandom = SecureRandom()): String {
        if (k == null) return CHIARO + testo
        val iv = ByteArray(IV_BYTE).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG_BIT, iv))
        c.updateAAD(nome.toByteArray(Charsets.UTF_8))
        return CIFRATO + hex(iv + c.doFinal(testo.toByteArray(Charsets.UTF_8)))
    }

    /** null se assente, illeggibile, manomesso o cifrato senza chiave disponibile. */
    fun decifra(k: SecretKey?, nome: String, salvato: String?): String? {
        if (salvato == null) return null
        if (salvato.startsWith(CHIARO)) return salvato.substring(CHIARO.length)
        if (!salvato.startsWith(CIFRATO) || k == null) return null
        return runCatching {
            val dati = daHex(salvato.substring(CIFRATO.length))
            require(dati.size > IV_BYTE)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BIT, dati.copyOfRange(0, IV_BYTE)))
            c.updateAAD(nome.toByteArray(Charsets.UTF_8))
            String(c.doFinal(dati.copyOfRange(IV_BYTE, dati.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    // Esadecimale invece di Base64: java.util.Base64 è API 26 e android.util.Base64 non gira nei test JVM.
    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    fun daHex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }
}

/**
 * Chiave AES-256 casuale avvolta con RSA-2048 di AndroidKeyStore (KeyPairGeneratorSpec, API 18-22).
 * La chiave avvolta sta nelle prefs dell'archivio; quella RSA non esce mai dal Keystore.
 */
class FornitoreChiaveKeystore(private val c: Context, private val prefs: SharedPreferences) : FornitoreChiave {
    override fun chiave(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            // Coppia nuova: una eventuale chiave avvolta con la coppia precedente non è più leggibile.
            prefs.edit().remove(CHIAVE_AVVOLTA).commit()
            generaCoppia()
        }
        val voce = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        prefs.getString(CHIAVE_AVVOLTA, null)?.let { avvolta ->
            val rsa = cifrarioRsa()
            rsa.init(Cipher.DECRYPT_MODE, voce.privateKey)
            val grezza = rsa.doFinal(CifraturaArchivio.daHex(avvolta))
            require(grezza.size == 32) { "chiave avvolta non valida" }
            return SecretKeySpec(grezza, "AES")
        }
        val grezza = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val rsa = cifrarioRsa()
        rsa.init(Cipher.ENCRYPT_MODE, voce.certificate.publicKey)
        check(prefs.edit().putString(CHIAVE_AVVOLTA, CifraturaArchivio.hex(rsa.doFinal(grezza))).commit()) { "chiave non salvata" }
        return SecretKeySpec(grezza, "AES")
    }

    @Suppress("DEPRECATION")
    private fun generaCoppia() {
        val inizio = Calendar.getInstance()
        val fine = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = android.security.KeyPairGeneratorSpec.Builder(c)
            .setAlias(ALIAS)
            .setSubject(X500Principal("CN=SpotLive archivio legacy"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(inizio.time)
            .setEndDate(fine.time)
            .setKeySize(2048)
            .build()
        KeyPairGenerator.getInstance("RSA", KEYSTORE).apply { initialize(spec) }.generateKeyPair()
    }

    // Sotto API 23 le chiavi di AndroidKeyStore si usano tramite il provider AndroidOpenSSL.
    private fun cifrarioRsa(): Cipher =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) Cipher.getInstance(RSA, "AndroidOpenSSL")
        else Cipher.getInstance(RSA)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "spotlive_archivio_legacy_rsa"
        private const val RSA = "RSA/ECB/PKCS1Padding"
        private const val CHIAVE_AVVOLTA = "_chiave_avvolta"
    }
}
