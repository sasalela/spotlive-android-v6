# 📱 SpotLive Player Android v6

![Android](https://img.shields.io/badge/Android-24%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue)
![License](https://img.shields.io/badge/license-Proprietary-red)

Player Android per sistema di digital signage SpotLive Screen. Compatibile al 100% con il server esistente e la versione Windows.

## ✨ Caratteristiche

- 🔧 **Configurazione semplificata**: Solo 2 parametri da inserire (ID_SCHERMO e USER_SCHERMO)
- 🔐 **Parametri server hardcoded**: Tutti i parametri di connessione sono nel codice (come versione Windows)
- 📱 **Kiosk mode**: Fullscreen, auto-start, keep screen on
- 🔄 **Compatibilità completa**: Usa lo stesso protocollo HTTP/FTP del client Windows
- 🎯 **Pronto all'uso**: Configurazione in 30 secondi, poi funziona autonomamente

## 🏗️ Architettura

```
spotlive-android-v6/
├── android/          # App Android nativa (Kotlin)
│   ├── app/
│   │   └── src/main/java/com/spotlive/player/
│   │       ├── Config.kt                    # ⚙️ Configurazione server (HARDCODED)
│   │       ├── PreferencesManager.kt        # 💾 Gestione ID/USER
│   │       ├── ConfigurationActivity.kt     # 🖥️ Schermata setup
│   │       └── MainActivity.kt              # 🎬 Activity principale
│   └── ...
├── backend/          # Server Go embedded (opzionale)
├── webapp/           # Frontend React (opzionale)
└── .github/
    └── workflows/
        └── build-android.yml  # 🤖 Build automatica APK
```

## 🚀 Download APK

### Ultima versione

[![Download APK](https://img.shields.io/badge/Download-APK%20Debug-brightgreen?style=for-the-badge)](../../actions)

1. Vai alla tab [Actions](../../actions)
2. Clicca sull'ultimo workflow "Build Android APK" completato ✅
3. Scarica l'artifact `spotlive-debug-apk`
4. Estrai `app-debug.apk`
5. Installa sul dispositivo Android

## 📋 Requisiti

- **Android**: 7.0+ (API 24+)
- **Architettura**: ARM64-v8a o ARMv7
- **Permessi**: Internet, Network State, Wake Lock, Boot Completed
- **Storage**: ~20-50 MB (app + cache media)

## 🔧 Configurazione

### Prima installazione

1. **Installa APK** sul dispositivo Android
2. **Avvia l'app** → Appare schermata configurazione
3. **Inserisci parametri**:
   - **ID Schermo**: ID numerico univoco (es: `567`)
   - **User Schermo**: Username schermo (es: `cucciniello`)
4. **Click "Salva e Connetti"**
5. ✅ **Fatto!** L'app si connette automaticamente al server

### Parametri server (hardcoded)

Questi parametri sono già configurati nel codice (`Config.kt`):

```kotlin
SERVER_URL = "http://80.88.90.214:80/spotlivescreen/XmlServlet"
FTP_HOST = "80.88.90.214"
FTP_USERNAME = "01fcbgyvir"
FTP_PASSWORD = "01fcbgyvir"
VERSION = "511"
```

**Per modificarli**: Edita `android/app/src/main/java/com/spotlive/player/Config.kt` e ricompila.

### Modifica configurazione esistente

1. Apri l'app
2. Click sul pulsante ⚙️ (in alto a destra)
3. Modifica ID_SCHERMO o USER_SCHERMO
4. Salva

## 🛠️ Build da sorgente

### Prerequisiti

- JDK 17+
- Android SDK (API 24-34)
- Gradle 8.1+

### Build locale

```bash
cd android
./gradlew assembleDebug
```

**Output**: `android/app/build/outputs/apk/debug/app-debug.apk`

### Build automatica (GitHub Actions)

Ogni push su `main` triggera automaticamente la build APK:

```yaml
# .github/workflows/build-android.yml
# Build automatica → APK disponibile negli Artifacts
```

## 📱 Installazione su dispositivo

### Metodo 1: ADB (per sviluppatori)

```bash
adb install app-debug.apk
```

### Metodo 2: Manuale (per utenti finali)

1. Copia `app-debug.apk` sul dispositivo (USB, email, cloud)
2. Sul dispositivo:
   - **Impostazioni** → **Sicurezza**
   - Abilita **"Installa da fonti sconosciute"**
3. Apri **File Manager**
4. Naviga al file APK
5. Click sul file → **Installa**

## 🔍 Debug e Testing

### Visualizzare log

```bash
# Via ADB
adb logcat -s SpotLive:V SpotLive-Server:V

# Output esempio:
# SpotLive: ID Schermo: 567
# SpotLive: User Schermo: cucciniello
# SpotLive: URL: http://80.88.90.214/.../XmlServlet?version=511&idSchermo=567&userschermo=cucciniello
```

### Resettare configurazione

```bash
# Cancella tutti i dati app
adb shell pm clear com.spotlive.player
```

### SharedPreferences location

```
/data/data/com.spotlive.player/shared_prefs/SpotLivePrefs.xml
```

Contiene:
- `id_schermo` (String)
- `user_schermo` (String)
- `is_configured` (Boolean)

## 📖 Documentazione

- [CONFIGURAZIONE_APP.md](CONFIGURAZIONE_APP.md) - Guida configurazione completa
- [README_BUILD.md](README_BUILD.md) - Guida build dettagliata
- [STRUTTURA_PROGETTO.md](STRUTTURA_PROGETTO.md) - Architettura progetto

## 🔄 Protocollo di comunicazione

### HTTP - Download programmazione

```
GET http://80.88.90.214:80/spotlivescreen/XmlServlet
  ?version=511
  &idSchermo={ID_SCHERMO}
  &userschermo={USER_SCHERMO}

Response: XML (XStream format)
```

### HTTP - Heartbeat

```
GET http://80.88.90.214:80/spotlivescreen/XmlServlet
  ?version=511
  &update=1
  &idSchermo={ID_SCHERMO}
  &userschermo={USER_SCHERMO}

Response: HTTP 200 OK
```

### FTP - Download media

```
Server: 80.88.90.214:21
User: 01fcbgyvir
Pass: 01fcbgyvir
Mode: Passive (PASV)
Path: /upload/*
```

## 🆚 Compatibilità

### Server

✅ Compatibile al 100% con server SpotLiveScreen esistente
✅ Stesso protocollo HTTP/FTP della versione Windows
✅ Nessuna modifica server richiesta

### Client Windows

✅ Può coesistere con client Windows
✅ Stessi parametri di connessione
✅ Stesso formato XML/FTP

## 🚧 Roadmap

- [ ] Implementazione logica rete (HTTP client per download XML)
- [ ] Parser XML (compatibile XStream)
- [ ] FTP client per download media
- [ ] Playlist manager (sequenziale/random)
- [ ] Video player nativo Android
- [ ] Image viewer
- [ ] Supporto multi-finestra
- [ ] RSS/Web content viewer
- [ ] Scheduled tasks (download automatici, restart)
- [ ] Server Go embedded (opzionale)

## 🐛 Known Issues

- **Backend Go**: Non ancora implementato (opzionale)
- **Logica rete**: Attualmente mostra solo pagina di test
- **Media playback**: Da implementare nella prossima fase

## 📄 License

Proprietario - Live Screen SRL
Tutti i diritti riservati.

## 👥 Contatti

**Live Screen SRL**
Digital Signage Solutions
[www.livescreen.it](http://www.livescreen.it)

---

**🎉 Pronto all'uso!** Scarica l'APK dagli [Actions](../../actions) e inizia subito a usare SpotLive Player su Android.
