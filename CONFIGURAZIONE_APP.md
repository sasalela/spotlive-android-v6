# 📱 SpotLive Player Android - Guida Configurazione

## ✅ IMPLEMENTAZIONE COMPLETATA

L'app Android è stata configurata esattamente come richiesto, seguendo il modello della versione Windows.

---

## 📋 FILE CREATI/MODIFICATI

### 1. **Config.kt** (NUOVO)
**Percorso**: `android/app/src/main/java/com/spotlive/player/Config.kt`

**Scopo**: Configurazione centralizzata con tutti i parametri server hardcoded.

**Parametri principali**:
```kotlin
SERVER_URL = "http://80.88.90.214:80/spotlivescreen/XmlServlet"
SERVER_HOST = "80.88.90.214"
VERSION = "511"

FTP_PORT = 21
FTP_USERNAME = "01fcbgyvir"
FTP_PASSWORD = "01fcbgyvir"
FTP_DIRECTORY = "/"
FTP_CONNECTION_MODE = 3  // Passive Mode

DELAY_MS = 20000L
CACHE_SECONDS = 5
TOLERANCE_SECONDS = 12
```

**Come modificare**:
- Apri il file `Config.kt`
- Modifica il valore di `SERVER_URL`, `SERVER_HOST`, o altri parametri
- Ricompila l'APK

**Funzioni utility**:
```kotlin
Config.buildProgrammationUrl(idSchermo, userSchermo)
  // Ritorna: http://80.88.90.214:80/spotlivescreen/XmlServlet?version=511&idSchermo=XXX&userschermo=YYY

Config.buildUpdateUrl(idSchermo, userSchermo)
  // Ritorna: http://80.88.90.214:80/spotlivescreen/XmlServlet?version=511&update=1&idSchermo=XXX&userschermo=YYY
```

---

### 2. **PreferencesManager.kt** (NUOVO)
**Percorso**: `android/app/src/main/java/com/spotlive/player/PreferencesManager.kt`

**Scopo**: Gestisce il salvataggio/recupero di ID_SCHERMO e USER_SCHERMO.

**SharedPreferences utilizzate**:
- **Nome file**: `SpotLivePrefs` (costante `Config.PREFS_NAME`)
- **Chiavi**:
  - `id_schermo` → Salva ID_SCHERMO (costante `Config.PREF_KEY_ID_SCHERMO`)
  - `user_schermo` → Salva USER_SCHERMO (costante `Config.PREF_KEY_USER_SCHERMO`)
  - `is_configured` → Flag booleano che indica se l'app è configurata

**Metodi principali**:
```kotlin
prefsManager.isConfigured()  // true se ID e USER sono salvati
prefsManager.saveConfiguration(idSchermo, userSchermo)
prefsManager.getIdSchermo()  // Ritorna ID salvato o null
prefsManager.getUserSchermo()  // Ritorna USER salvato o null
prefsManager.getProgrammationUrl()  // URL completo per download XML
prefsManager.getUpdateUrl()  // URL completo per heartbeat
prefsManager.clearConfiguration()  // Reset completo
```

**Dove sono salvati i dati**:
```
/data/data/com.spotlive.player/shared_prefs/SpotLivePrefs.xml
```

---

### 3. **ConfigurationActivity.kt** (NUOVO)
**Percorso**: `android/app/src/main/java/com/spotlive/player/ConfigurationActivity.kt`

**Scopo**: Schermata di configurazione iniziale per inserire ID_SCHERMO e USER_SCHERMO.

**Quando viene mostrata**:
1. **Primo avvio** → Se `isConfigured() == false`
2. **Click su pulsante ⚙ Impostazioni** → Dalla MainActivity

**Comportamento**:
- Mostra 2 campi input:
  - ID Schermo (numerico)
  - User Schermo (testo)
- Validazione:
  - Entrambi i campi obbligatori
  - ID deve essere numerico
- Dopo salvataggio:
  - Salva in SharedPreferences
  - Reindirizza a MainActivity
  - Log di debug in logcat

**UI**:
- Tema scuro (background #1E1E1E)
- Pulsante verde "Salva e Connetti"
- Pulsante grigio "Annulla" (visibile solo se già configurato)
- Mostra info server (read-only)

---

### 4. **MainActivity.kt** (MODIFICATO)
**Percorso**: `android/app/src/main/java/com/spotlive/player/MainActivity.kt`

**Modifiche principali**:

1. **Check configurazione all'avvio**:
```kotlin
if (!prefsManager.isConfigured()) {
    showConfigurationScreen()  // Vai a ConfigurationActivity
    return
}
// Altrimenti procedi normalmente
```

2. **Pulsante Impostazioni**:
   - Piccolo pulsante ⚙ in alto a destra
   - Semi-trasparente (alpha 0.3)
   - Click → Apre ConfigurationActivity

3. **Pagina di test**:
   - Al posto del server Go embedded (commentato per ora)
   - Mostra HTML statico con configurazione corrente:
     - ID Schermo
     - User Schermo
     - Server URL
     - URL Programmazione completo
     - Versione

4. **Logging**:
   - All'avvio mostra `getConfigurationSummary()` nel logcat
   - Tag: `SpotLive`

**Funzione embedded server** (commentata):
```kotlin
// startEmbeddedServer()  // COMMENTATO per prima versione test
```
Quando scommentata, passa ID e USER al server Go:
```kotlin
--id-schermo $idSchermo
--user-schermo $userSchermo
```

---

### 5. **AndroidManifest.xml** (MODIFICATO)
**Percorso**: `android/app/src/main/AndroidManifest.xml`

**Aggiunta**:
```xml
<activity
    android:name=".ConfigurationActivity"
    android:exported="false"
    android:screenOrientation="landscape"
    android:configChanges="orientation|screenSize|keyboard|keyboardHidden" />
```

---

## 🔄 FLUSSO APPLICAZIONE

### Primo avvio (NON configurato):
```
1. App si avvia (MainActivity.onCreate)
   ↓
2. PreferencesManager.isConfigured() → false
   ↓
3. Mostra ConfigurationActivity
   ↓
4. Utente inserisce:
   - ID_SCHERMO (es: 567)
   - USER_SCHERMO (es: cucciniello)
   ↓
5. Click "Salva e Connetti"
   ↓
6. Validazione input
   ↓
7. PreferencesManager.saveConfiguration(...)
   ↓
8. Salva in SharedPreferences:
   - SpotLivePrefs.xml
   - id_schermo = "567"
   - user_schermo = "cucciniello"
   - is_configured = true
   ↓
9. Reindirizza a MainActivity
   ↓
10. MainActivity carica pagina test
```

### Avvi successivi (GIÀ configurato):
```
1. App si avvia (MainActivity.onCreate)
   ↓
2. PreferencesManager.isConfigured() → true
   ↓
3. Legge da SharedPreferences:
   - id_schermo
   - user_schermo
   ↓
4. Costruisce URL programmazione:
   http://80.88.90.214:80/spotlivescreen/XmlServlet?version=511&idSchermo=567&userschermo=cucciniello
   ↓
5. Carica pagina principale (test HTML o server Go)
```

### Modifica configurazione:
```
1. Click pulsante ⚙ in MainActivity
   ↓
2. Apre ConfigurationActivity
   ↓
3. Mostra valori attuali nei campi
   ↓
4. Utente modifica e salva
   ↓
5. PreferencesManager.saveConfiguration(...)
   ↓
6. Torna a MainActivity
   ↓
7. MainActivity rilegge nuovi valori
```

---

## 🧪 TESTING E DEBUG

### Visualizzare i log:
```bash
# Da terminale con device connesso via ADB
adb logcat -s SpotLive:V SpotLive-Server:V

# Output esempio:
SpotLive: === SpotLive Configuration ===
SpotLive: Configured: true
SpotLive: ID Schermo: 567
SpotLive: User Schermo: cucciniello
SpotLive: Server URL: http://80.88.90.214:80/spotlivescreen/XmlServlet
SpotLive: ==============================
```

### Resettare configurazione (debug):
```bash
# Via ADB
adb shell pm clear com.spotlive.player

# Oppure:
adb shell run-as com.spotlive.player rm -rf /data/data/com.spotlive.player/shared_prefs/
```

### Visualizzare SharedPreferences (dispositivi rooted/emulator):
```bash
adb shell cat /data/data/com.spotlive.player/shared_prefs/SpotLivePrefs.xml

# Output:
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="id_schermo">567</string>
    <string name="user_schermo">cucciniello</string>
    <boolean name="is_configured" value="true" />
</map>
```

---

## 📦 BUILD APK

### Build debug (per test):
```bash
cd android
./gradlew assembleDebug

# Output:
# android/app/build/outputs/apk/debug/app-debug.apk
```

### Build release (per produzione):
```bash
./gradlew assembleRelease

# Output:
# android/app/build/outputs/apk/release/app-release-unsigned.apk
```

### Via GitHub Actions:
Il progetto è pronto per una build automatica via GitHub Actions.
Esempio workflow `.github/workflows/build.yml`:

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x android/gradlew

      - name: Build debug APK
        working-directory: ./android
        run: ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 PERSONALIZZAZIONE

### Cambiare SERVER_URL:

**Percorso**: `android/app/src/main/java/com/spotlive/player/Config.kt`

```kotlin
// PRIMA:
const val SERVER_URL = "http://80.88.90.214:80/spotlivescreen/XmlServlet"

// DOPO (esempio nuovo server):
const val SERVER_URL = "http://192.168.1.100:8080/spotlive/xml"
```

Ricompila APK dopo la modifica.

### Cambiare credenziali FTP:

**Percorso**: `android/app/src/main/java/com/spotlive/player/Config.kt`

```kotlin
const val FTP_USERNAME = "nuovo_user"
const val FTP_PASSWORD = "nuova_password"
```

### Nascondere pulsante Impostazioni:

**Percorso**: `android/app/src/main/java/com/spotlive/player/MainActivity.kt`

Commenta/rimuovi queste righe (circa linea 102-122):
```kotlin
// settingsButton = Button(this).apply { ... }
// container.addView(settingsButton, btnParams)
```

Oppure implementa una gesture segreta (es: tap 5 volte in un angolo).

### Abilitare server Go embedded:

**Percorso**: `android/app/src/main/java/com/spotlive/player/MainActivity.kt`

Linea 51, scommenta:
```kotlin
// PRIMA:
// startEmbeddedServer()

// DOPO:
startEmbeddedServer()
```

E alla linea 355, cambia:
```kotlin
// PRIMA:
webView.loadDataWithBaseURL(null, testHtml, "text/html", "UTF-8", null)

// DOPO:
val url = Config.buildLocalServerUrl()
webView.loadUrl(url)
```

---

## ✅ CHECKLIST COMPLETAMENTO

- [x] Config.kt creato con parametri server hardcoded
- [x] PreferencesManager.kt per gestione ID/USER
- [x] ConfigurationActivity.kt per schermata configurazione
- [x] MainActivity.kt modificato per flow configurazione
- [x] AndroidManifest.xml aggiornato
- [x] SharedPreferences keys documentate
- [x] Flusso primo avvio → configurazione → avvii successivi
- [x] Pulsante Impostazioni per modifiche future
- [x] Validazione input (ID numerico, campi obbligatori)
- [x] Logging completo per debug
- [x] Configurazione Gradle coerente
- [x] gradlew presente ed eseguibile
- [x] Pronto per build `./gradlew assembleDebug`

---

## 🚀 PROSSIMI PASSI

1. **Build APK**:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

2. **Installare su device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Test primo avvio**:
   - Inserire ID_SCHERMO e USER_SCHERMO
   - Verificare salvataggio
   - Verificare URL generato corretto

4. **Implementare logica rete** (fase successiva):
   - HTTP client per download XML
   - Parser XML (XStream o equivalente)
   - FTP client per download media
   - Playlist manager
   - Video player

5. **Integrare backend Go** (opzionale):
   - Buildare backend per Android ARM64
   - Copiare in `android/app/src/main/assets/`
   - Scommentare `startEmbeddedServer()`
   - Passare ID/USER via args al server

---

## 📝 NOTE IMPORTANTI

1. **Parametri hardcoded**: Tutti i parametri server (URL, FTP, credenziali) sono nel codice come nella versione Windows. Per cambiarli serve ricompilare.

2. **Solo 2 parametri utente**: L'utente configura SOLO ID_SCHERMO e USER_SCHERMO. Tutto il resto è trasparente.

3. **Persistenza**: I parametri utente sono salvati in SharedPreferences e sopravvivono a riavvii/aggiornamenti app.

4. **Kiosk mode**: L'app va in fullscreen e disabilita back button (come Windows).

5. **Auto-start**: BootReceiver configurato per avvio automatico dopo reboot device.

6. **Landscape only**: App forzata in orientamento landscape.

7. **Keep screen on**: Lo schermo rimane sempre acceso.

8. **Cleartext traffic**: Abilitato per HTTP (non HTTPS).

---

**Fatto! L'app è pronta per la build e test. 🎉**
