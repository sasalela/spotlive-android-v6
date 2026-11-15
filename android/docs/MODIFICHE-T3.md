<!-- SVILUPPO-20261114-03: aggiunta la sezione «Esito build e prove SVILUPPO-20261114-03». -->
# Variante `legacy` del player Android (Taurus T3, Android 5.1 / API 22)

Job: **SVILUPPO-20261114-02** (piano: `../ANALISI.md`, job SVILUPPO-20261114-01). Base: 1.0.15 (versionCode 16).
Nessun segreto in questo documento né nel codice.

## Cosa cambia

### Gradle (`app/build.gradle.kts`)
- Dimensione `piattaforma`, due flavor dello **stesso pacchetto** `net.spotlive.player`, stessa firma (variabili `SPOTLIVE_SIGNING_*`), stesso `versionCode` 16:
  - `standard`: minSdk 24, `VARIANTE="standard"`, `WEBVIEW_CHROME_MIN=0`, versionName `1.0.15`, `VERSIONE_PLAYER="1.0.15"`.
  - `legacy`: minSdk 22, `versionNameSuffix="-legacy"` → `1.0.15-legacy`, `VARIANTE="legacy"`, `WEBVIEW_CHROME_MIN=61`, `VERSIONE_PLAYER="1.0.15-legacy"`.
- Firma v1 lasciata attiva: Android 5.1 verifica solo la v1.
- Comandi: `./gradlew assembleStandardRelease` e `./gradlew assembleLegacyRelease`; uscite `app/build/outputs/apk/standard/release/` e `app/build/outputs/apk/legacy/release/` (prima: `assembleRelease`, `apk/release/`). Test: `testStandardDebugUnitTest`, `testLegacyDebugUnitTest`; lint: `lintStandardDebug`, `lintLegacyDebug`.

### Codice comune (`src/main`) — guardie API
Ogni guardia usa il livello API reale del metodo; il ramo ≥ soglia è il codice della 1.0.15.
- `piattaforma/Servizi.kt` (nuovo): `servizio<T>(c, Context.*_SERVICE)` (da API 23 `getSystemService(Class)` come prima) e `Variante` (stato legacy per la diagnosi).
- `sistema/AmministratoreDispositivo.kt`, `OrarioAccensione.kt` (anche `set` al posto di `setAndAllowWhileIdle` sotto 23), `Sentinella.kt` (callback di rete sotto 24, `INTERNET` al posto di `VALIDATED` sotto 23), `WatchdogServizio.kt` (canali solo da 26), `Finestrella.kt` (`TYPE_PHONE` sotto 26), `Configurazione.kt` (overlay/batteria sotto 23, notifiche sotto 24), `AvvioRicevitore.kt` (`ContextCompat.startForegroundService`), `PlayerApplicazione.kt`, `ponte/PonteGestore.kt` (lock task, rete, DPM), `MainActivity.kt` (lock task, rete, `onReceivedError` deprecato attivo solo sotto 23, `@RequiresApi(26)` su `onRenderProcessGone`).
- `AggiornatoreApk.silenzioso()`: da proprietario vale solo da API 23.
- Unici effetti possibili sopra API 23: su **API 24-25** la finestrella usa `TYPE_PHONE` e i canali di notifica non si creano (prima lì l'app andava in crash o la finestrella mancava). Nessun dispositivo noto in campo con quelle versioni. Su API ≥ 26 (T30) il comportamento è quello della 1.0.15.

### Solo `legacy` (sorgenti per flavor o `if` su costanti di BuildConfig, codice morto nella standard)
- `piattaforma/ArchivioSicuro.kt`: standard = file della 1.0.15 identico (`src/standard`); legacy = RSA-2048 in AndroidKeyStore (`KeyPairGeneratorSpec`) che avvolge una chiave AES-256 casuale, valori AES/GCM nel file `spotlive_archivio_legacy` (chiavi `token` e `taurus_password_terminale`, stessa API). Se il Keystore fallisce: valori in chiaro nello stesso file privato, evento nel Diario e `archivioCifrato=false` nello stato. Mai un crash all'avvio.
- `piattaforma/Tls.kt`: standard = identità (`applica(b)=b`, `socketFactory()=null`); legacy = CA di sistema (`AndroidCAStore`, solo `system:`) + ISRG Root X1/X2 (`src/legacy/res/raw/`), `TrustManagerFactory` standard, `ConnectionSpec.MODERN_TLS` (niente CLEARTEXT), `OkHostnameVerifier` invariato. Usato da `AggiornatoreApk`, `CacheNativa`, `ApplicatoreLuminosita`, `Diagnosi.prova` e dalla rilettura del manifest in `MainActivity.osservaManifest` (HttpsURLConnection). `LuminositaTaurus` (terminale locale) invariato.
- WebView sotto API 24 (`MainActivity.onReceivedSslError` + `piattaforma/WebViewLegacy.puoProcedere`, funzione pura): `proceed()` solo se l'errore è soltanto `SSL_UNTRUSTED`, l'host è quello di `URL_PLAYER` e la foglia (SHA-256 da `SslCertificate.saveState(...)["x509-certificate"]`) è uguale a quella verificata dal client nativo legacy. Altrimenti `cancel()` come prima. Foglia nuova: una sola nuova verifica nativa e poi ricarica.
- Aggiornamenti: la legacy legge **solo** `/aggiornamenti/android/legacy/versione-android.json` (niente endpoint per dispositivo, niente storico, canale non salvato). `RegolaAggiornamento.varianteCompatibile` (APK `-legacy` solo sulla legacy e viceversa) si applica prima della decisione (esito `variante_diversa`, registrato una volta, anche per l'istruzione inoltrata dal ponte) e dopo il download sul `versionName` reale dell'APK. Questo controllo è anche nella standard (dalla prossima release ordinaria).
- Diagnosi WebView (`WEBVIEW_CHROME_MIN > 0`): sotto la soglia riga fissa nel riquadro e nella striscia («WebView … (Chrome N): serve ≥ 61. Aggiornare “Android System WebView”») ed evento nel Diario; la pagina si carica comunque. `PonteGestore.stato()` legacy aggiunge `variante`, `webview` {pacchetto, versione, chrome, sufficiente}, `archivioCifrato`, `originiSconosciute`. La sonda nativa manda `User-Agent: SpotLivePlayer/1.0.15-legacy (Android 5.1; API 22; WebView <pacchetto> <versione>)`.
- Schermata di blocco sotto API 24: `FLAG_SHOW_WHEN_LOCKED`, più `FLAG_TURN_SCREEN_ON` solo se in orario.

### File toccati da SVILUPPO-20261114-02
`app/build.gradle.kts`; `src/main/.../PlayerApplicazione.kt`, `MainActivity.kt`, `Diagnosi.kt`, `aggiornamenti/AggiornamentoPlayer.kt`, `aggiornamenti/RegolaAggiornamento.kt`, `cache/CacheNativa.kt`, `luminosita/ApplicatoreLuminosita.kt` (solo il client HTTP), `ponte/PonteGestore.kt`, `sistema/{AmministratoreDispositivo,AvvioRicevitore,Configurazione,Finestrella,OrarioAccensione,Sentinella,WatchdogServizio}.kt`; nuovi `piattaforma/Servizi.kt`, `piattaforma/WebViewLegacy.kt`; `src/standard/.../piattaforma/{ArchivioSicuro,Tls}.kt`; `src/legacy/.../piattaforma/{ArchivioSicuro,Tls}.kt`; test `src/test/.../{aggiornamenti/RegolaAggiornamentoTest, piattaforma/WebViewLegacyTest, sistema/Api22Test (con Api30Test), sistema/ProprietarioApi22Test}.kt`, `src/testStandard/.../VarianteStandardTest.kt`, `src/testLegacy/.../{ArchivioLegacyTest, TlsLegacyTest, PlayerApplicazioneApi22Test}.kt`.
`src/main/.../piattaforma/ArchivioSicuro.kt` è rimasto come file vuoto (solo `package`): **va cancellato** (la sessione non poteva eliminare file).

## Esito build e prove SVILUPPO-20261114-03 (14/11/2026)
- `src/standard/.../ArchivioSicuro.kt` confrontato con la 1.0.15: identico.
- `gradle assembleStandardDebug`: riuscita → `app/build/outputs/apk/standard/debug/app-standard-debug.apk`.
- `gradle assembleLegacyDebug`: riuscita → `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`. Nessun errore di compilazione da correggere.
- `gradle testLegacyDebugUnitTest`: riuscita. 69 test, 69 passati, 0 falliti, 0 errori, 0 saltati (compresi `Api22Test`, `ProprietarioApi22Test`, `PlayerApplicazioneApi22Test`, `ArchivioLegacyTest`, `TlsLegacyTest`, `Api30Test`).
- `gradle testStandardDebugUnitTest`: fallita. 61 test, 52 passati, 9 falliti (`Api22Test` 5, `ProprietarioApi22Test` 4), 0 errori, 0 saltati. Causa: i due test con `@Config(sdk = [22])` stanno in `src/test`, che gira anche sulla standard (minSdk 24). Robolectric rifiuta il manifest (`PackageParserException` in `ShadowPackageParser`). Il codice della standard non c'entra: gli altri 52 test passano, tra cui `Api30Test` e `VarianteStandardTest`.
- Non risolto (comandi negati in questa sessione): `mkdir -p` e `mv` sotto `app/src/`. Restano da fare: (1) `ProprietarioApi22Test.kt` da `src/test/.../sistema/` a `src/testLegacy/.../sistema/`; (2) la classe `Api22Test` in un file `src/testLegacy/.../sistema/Api22Test.kt`, mentre `GuardieApiBase` e `Api30Test` restano in `src/test` (per esempio rinominando il file in `GuardieApiTest.kt`); (3) `src/main/.../piattaforma/ArchivioSicuro.kt`, ancora vuoto con solo `package`, da togliere dal source set `main`. Oggi compila perché non dichiara nulla.
- Non provati qui: installazione sull'emulatore Android 5.1, `aapt`, build di rilascio.

## Regole di pubblicazione
1. **Mai** un APK `-legacy` in `/aggiornamenti/android/stabile/`, `/aggiornamenti/android/prova/`, nel file storico `/scarica-…/versione-android.json` o nella risposta dell'endpoint per dispositivo `/api/v1/player/aggiornamento`. I T30 con 1.0.15 non controllano la variante: li protegge solo questa regola (e il versionCode uguale).
2. L'APK legacy va **solo** in `/aggiornamenti/android/legacy/` con il suo `versione-android.json` (stesso formato dei file di canale; `versionName` con suffisso `-legacy`, `sha256`, `certSha256`), solo per i T3.
3. Stesso versionCode per le due varianti della stessa versione (1.0.15 = 16; la prossima 17 e 17).
4. La standard 1.0.15 **non** si ripubblica: i T30 restano sulla 1.0.15 attuale fino alla prossima release ordinaria.
5. Firma: solo con le variabili d'ambiente `SPOTLIVE_SIGNING_*`, mai file `.p12`/keystore o password nel repository.

## Limiti noti
- **WebView del T3** (decisivo): con Chromium 39 il player web non parte; la legacy lo dice a schermo e nel Diario ma non riproduce spot finché la WebView non si aggiorna. Soglia 61 da confermare sul target del bundle web (87 se è il predefinito di Vite).
- La chiave `x509-certificate` di `SslCertificate` è interna AOSP: se manca, `cancel()` (fallimento sicuro). Non verificato se le connessioni `wss://` passino da `onReceivedSslError`.
- Keystore RSA del RK3368, installazione con PackageInstaller e «Origini sconosciute», `dpm set-device-owner`, overlay `TYPE_PHONE`, terminale Taurus locale: da provare sul T3.
- Orari di accensione: disattivati sotto API 26 come già nella 1.0.15. Installazione silenziosa: mai sotto API 23 (serve il tecnico).
- Archivio legacy senza Keystore: protezione = sandbox dell'app + `allowBackup=false`. I nomi delle chiavi nelle prefs legacy sono in chiaro (i valori no).
- Cambiando variante sullo stesso dispositivo (non previsto) archivio e fiducia TLS cambiano: password del terminale da ritirare di nuovo.
