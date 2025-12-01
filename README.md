# SpotLiveScreen Player Android v6.0

Player moderno per digital signage compatibile al 100% con il server SpotLiveScreen esistente.

## Architettura

```
┌─────────────────────────────────────┐
│   Android Device (Box TV / Tablet)  │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  Android App (APK)            │ │
│  ├───────────────────────────────┤ │
│  │  WebView (Kiosk Mode)         │ │
│  │  └─ PWA (React)               │ │
│  │                               │ │
│  │  Embedded Go Server :8080     │ │
│  │  ├─ HTTP Proxy → XmlServlet   │ │
│  │  └─ FTP Proxy → /upload       │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
              ↓
    Internet / LAN
              ↓
┌─────────────────────────────────────┐
│   Server Backend (NO MODIFICHE)     │
│   http://80.88.90.214:80            │
│   FTP :21                           │
└─────────────────────────────────────┘
```

## Componenti

### 1. `/backend` - Go Server
- Proxy HTTP per chiamate a `/XmlServlet`
- Proxy FTP per download media da `/upload/`
- Serve PWA statica
- API REST per configurazione

### 2. `/webapp` - PWA (React + TypeScript)
- Video player HTML5
- Image viewer
- Playlist manager (sequenziale/random)
- Scheduler (8 download + restart)
- Setup wizard
- Cache locale (IndexedDB)

### 3. `/android` - Android App
- WebView container
- Gestione Go server embedded
- Kiosk mode (fullscreen immersive)
- Auto-start al boot
- Permissions management

## Features

✅ Compatibilità 100% con server esistente
✅ Stesso protocollo HTTP + FTP
✅ Supporto 1000+ schermi
✅ Nessuna modifica server-side
✅ Credenziali cifrate
✅ Offline-first con cache
✅ Auto-update programmazione (8x/day)
✅ Restart automatico
✅ Kiosk mode per digital signage

## Build

```bash
# Backend Go
cd backend
GOOS=android GOARCH=arm64 go build -o spotlive-server

# Frontend PWA
cd webapp
npm install
npm run build

# Android APK
cd android
./gradlew assembleRelease
```

## Installazione

1. Trasferisci `SpotLivePlayer.apk` su dispositivo Android
2. Abilita "Installazione da fonti sconosciute"
3. Installa APK
4. Apri app → Wizard configurazione
5. Inserisci credenziali (username, password, idMonitor)
6. Salva → Player parte automaticamente

## Configurazione

Al primo avvio, wizard richiede:
- **Server URL**: http://80.88.90.214:80 (default)
- **Username**: Es. 01fcbgyvir
- **Password**: Es. 01fcbgyvir
- **ID Monitor**: Es. 567
- **User Schermo**: Es. cucciniello (opzionale)

Config salvata cifrata in `/data/data/com.spotlive.player/`

## Compatibilità

- Android 7.0+ (API 24+)
- ARM64 / ARMv7
- Mini PC Android
- Android TV boxes
- Tablet Android

## License

Proprietario - SpotLiveScreen
