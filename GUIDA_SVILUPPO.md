# Guida Sviluppo SpotLiveScreen Player Android v6.0

## 🎯 STATO ATTUALE

Ho creato la struttura completa del progetto con:

### ✅ Backend Go (COMPLETO)
- `/backend/internal/config` - Gestione configurazione cifrata
- `/backend/internal/ftp` - Client FTP per download media
- `/backend/internal/xml` - Parser XML programmazione
- `/backend/internal/server` - HTTP handlers (API REST)
- `/backend/cmd/main.go` - Server principale

**API Implementate**:
```
GET  /api/config          - Ottieni configurazione
POST /api/config          - Salva configurazione
POST /api/config/test     - Testa connessione server
GET  /api/schedule        - Scarica programmazione XML
GET  /api/media/:filename - Download media via FTP proxy
GET  /api/cache/:filename - Media dalla cache
POST /api/media/download-all - Download tutti i media
GET  /api/status          - Status player
POST /api/heartbeat       - Invia update al server
```

### 📋 DA COMPLETARE

#### 1. Frontend PWA (React + TypeScript)

**File da creare in `/webapp/src/`**:

```typescript
// types/index.ts - TypeScript interfaces
export interface Config {
  serverUrl: string;
  username: string;
  password: string;
  idMonitor: string;
  userSchermo?: string;
}

export interface Schermo {
  id: number;
  nome: string;
  larghezza: number;
  altezza: number;
  elenco: boolean; // true=sequenziale, false=random
  oraDownload01-08: string[];
  oraRestart: string;
  finestre: Finestra[];
}

export interface Media {
  id: number;
  nome: string;
  tipo: 'VIDEO' | 'IMAGE' | 'RSS' | 'WEB';
  video?: string;
  immagine?: string;
  tempo: number; // secondi (0=auto)
  audio: boolean;
}

// services/api.ts - API client
export class API {
  private baseUrl = 'http://localhost:8080/api';

  async getConfig(): Promise<Config> {
    const res = await fetch(`${this.baseUrl}/config`);
    return res.json();
  }

  async saveConfig(config: Config) {
    const res = await fetch(`${this.baseUrl}/config`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(config)
    });
    return res.json();
  }

  async testConnection() {
    const res = await fetch(`${this.baseUrl}/config/test`, {method: 'POST'});
    return res.json();
  }

  async getSchedule() {
    const res = await fetch(`${this.baseUrl}/schedule`);
    return res.json();
  }

  async downloadAllMedia() {
    const res = await fetch(`${this.baseUrl}/media/download-all`, {method: 'POST'});
    return res.json();
  }

  getMediaUrl(filename: string): string {
    return `${this.baseUrl}/media/${encodeURIComponent(filename)}`;
  }
}

// services/storage.ts - IndexedDB cache
import { openDB } from 'idb';

export class StorageService {
  private dbName = 'spotlive-cache';
  private db;

  async init() {
    this.db = await openDB(this.dbName, 1, {
      upgrade(db) {
        db.createObjectStore('media');
        db.createObjectStore('schedule');
      }
    });
  }

  async cacheMedia(filename: string, blob: Blob) {
    await this.db.put('media', blob, filename);
  }

  async getMedia(filename: string): Promise<string | null> {
    const blob = await this.db.get('media', filename);
    if (blob) return URL.createObjectURL(blob);
    return null;
  }

  async saveSchedule(schedule: any) {
    await this.db.put('schedule', schedule, 'current');
  }

  async getSchedule() {
    return this.db.get('schedule', 'current');
  }
}

// components/VideoPlayer.tsx - Player video
import React, { useEffect, useRef, useState } from 'react';

export const VideoPlayer: React.FC<{
  media: Media;
  onEnded: () => void;
}> = ({ media, onEnded }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [videoUrl, setVideoUrl] = useState<string>();

  useEffect(() => {
    const api = new API();
    const url = api.getMediaUrl(media.video!);
    setVideoUrl(url);
  }, [media]);

  return (
    <video
      ref={videoRef}
      src={videoUrl}
      autoPlay
      muted={!media.audio}
      onEnded={onEnded}
      style={{
        width: '100vw',
        height: '100vh',
        objectFit: 'cover',
        backgroundColor: '#000'
      }}
    />
  );
};

// components/ImageViewer.tsx - Viewer immagini
export const ImageViewer: React.FC<{
  media: Media;
  duration: number;
  onEnded: () => void;
}> = ({ media, duration, onEnded }) => {
  const api = new API();
  const imageUrl = api.getMediaUrl(media.immagine!);

  useEffect(() => {
    const timer = setTimeout(onEnded, duration * 1000);
    return () => clearTimeout(timer);
  }, [duration, onEnded]);

  return (
    <div style={{
      width: '100vw',
      height: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: '#000'
    }}>
      <img
        src={imageUrl}
        alt={media.nome}
        style={{
          maxWidth: '100%',
          maxHeight: '100%',
          objectFit: 'contain'
        }}
      />
    </div>
  );
};

// components/SetupWizard.tsx - Wizard configurazione
export const SetupWizard: React.FC<{onComplete: () => void}> = ({onComplete}) => {
  const [config, setConfig] = useState({
    serverUrl: 'http://80.88.90.214:80',
    username: '',
    password: '',
    idMonitor: '',
    userSchermo: ''
  });
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState('');

  const handleTest = async () => {
    setTesting(true);
    setError('');
    try {
      const api = new API();
      await api.saveConfig(config);
      const result = await api.testConnection();
      if (result.success) {
        alert('Connessione riuscita! Schermo: ' + result.schermo);
      } else {
        setError(result.error);
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    try {
      const api = new API();
      await api.saveConfig(config);
      onComplete();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div style={{padding: 20, maxWidth: 500, margin: '0 auto'}}>
      <h1>SpotLiveScreen Setup</h1>
      <div>
        <label>Server URL:</label>
        <input
          type="text"
          value={config.serverUrl}
          onChange={e => setConfig({...config, serverUrl: e.target.value})}
          style={{width: '100%', padding: 8, marginBottom: 10}}
        />
      </div>
      <div>
        <label>Username:</label>
        <input
          type="text"
          value={config.username}
          onChange={e => setConfig({...config, username: e.target.value})}
          style={{width: '100%', padding: 8, marginBottom: 10}}
        />
      </div>
      <div>
        <label>Password:</label>
        <input
          type="password"
          value={config.password}
          onChange={e => setConfig({...config, password: e.target.value})}
          style={{width: '100%', padding: 8, marginBottom: 10}}
        />
      </div>
      <div>
        <label>ID Monitor:</label>
        <input
          type="text"
          value={config.idMonitor}
          onChange={e => setConfig({...config, idMonitor: e.target.value})}
          style={{width: '100%', padding: 8, marginBottom: 10}}
        />
      </div>
      <div>
        <label>User Schermo (opzionale):</label>
        <input
          type="text"
          value={config.userSchermo}
          onChange={e => setConfig({...config, userSchermo: e.target.value})}
          style={{width: '100%', padding: 8, marginBottom: 10}}
        />
      </div>
      {error && <div style={{color: 'red', marginBottom: 10}}>{error}</div>}
      <button onClick={handleTest} disabled={testing} style={{marginRight: 10, padding: 10}}>
        {testing ? 'Testing...' : 'Test Connessione'}
      </button>
      <button onClick={handleSave} style={{padding: 10}}>Salva e Avvia</button>
    </div>
  );
};

// components/Player.tsx - Player principale
export const Player: React.FC = () => {
  const [schedule, setSchedule] = useState<any>(null);
  const [currentMedia, setCurrentMedia] = useState<Media | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [playlist, setPlaylist] = useState<Media[]>([]);

  useEffect(() => {
    // Carica programmazione
    const loadSchedule = async () => {
      const api = new API();
      const result = await api.getSchedule();
      if (result.success) {
        setSchedule(result.schedule);
        buildPlaylist(result.schedule);
        // Download media
        await api.downloadAllMedia();
      }
    };
    loadSchedule();
  }, []);

  const buildPlaylist = (sched: any) => {
    const mediaList = sched.mediaFinestre
      .sort((a, b) => a.ordine - b.ordine)
      .map(mf => mf.media);

    setPlaylist(mediaList);
    setCurrentMedia(mediaList[0]);
  };

  const nextMedia = () => {
    let nextIdx = currentIndex + 1;

    if (schedule.schermo.elenco) {
      // Sequenziale
      if (nextIdx >= playlist.length) nextIdx = 0;
    } else {
      // Random
      nextIdx = Math.floor(Math.random() * playlist.length);
    }

    setCurrentIndex(nextIdx);
    setCurrentMedia(playlist[nextIdx]);
  };

  if (!currentMedia) return <div>Loading...</div>;

  return (
    <div>
      {currentMedia.tipo === 'VIDEO' && (
        <VideoPlayer media={currentMedia} onEnded={nextMedia} />
      )}
      {currentMedia.tipo === 'IMAGE' && (
        <ImageViewer
          media={currentMedia}
          duration={currentMedia.tempo || 10}
          onEnded={nextMedia}
        />
      )}
    </div>
  );
};

// App.tsx - App principale
import React, { useEffect, useState } from 'react';
import { SetupWizard } from './components/SetupWizard';
import { Player } from './components/Player';
import { API } from './services/api';

export const App: React.FC = () => {
  const [configured, setConfigured] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkConfig = async () => {
      const api = new API();
      const result = await api.getConfig();
      setConfigured(result.configured);
      setLoading(false);
    };
    checkConfig();
  }, []);

  if (loading) return <div>Loading...</div>;

  if (!configured) {
    return <SetupWizard onComplete={() => setConfigured(true)} />;
  }

  return <Player />;
};
```

#### 2. Scheduler Background

```typescript
// hooks/useScheduler.ts
import { useEffect } from 'react';
import { API } from '../services/api';

export function useScheduler(schedule: any) {
  useEffect(() => {
    if (!schedule) return;

    // Download schedulati
    const downloadTimes = schedule.schermo.getDownloadSchedule();
    const jobs = downloadTimes.map(time => {
      return scheduleAt(time, async () => {
        const api = new API();
        await api.getSchedule();
        await api.downloadAllMedia();
      });
    });

    // Restart automatico
    const restartJob = scheduleAt(schedule.schermo.oraRestart, () => {
      window.location.reload();
    });

    // Heartbeat ogni 5 minuti
    const heartbeatInterval = setInterval(async () => {
      const api = new API();
      await api.sendHeartbeat();
    }, 5 * 60 * 1000);

    return () => {
      jobs.forEach(j => j.cancel());
      restartJob.cancel();
      clearInterval(heartbeatInterval);
    };
  }, [schedule]);
}

function scheduleAt(time: string, callback: () => void) {
  // Parse time (HH:MM:SS)
  const [hours, minutes] = time.split(':').map(Number);

  const now = new Date();
  const scheduled = new Date();
  scheduled.setHours(hours, minutes, 0, 0);

  if (scheduled < now) {
    scheduled.setDate(scheduled.getDate() + 1);
  }

  const delay = scheduled.getTime() - now.getTime();
  const timeout = setTimeout(callback, delay);

  return {
    cancel: () => clearTimeout(timeout)
  };
}
```

#### 3. Android App

**File da creare in `/android/`**:

```kotlin
// MainActivity.kt
package com.spotlive.player

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var serverProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive (kiosk mode)
        enableKioskMode()

        // Avvia Go server embedded
        startEmbeddedServer()

        // WebView setup
        webView = WebView(this)
        setupWebView()

        setContentView(webView)

        // Carica app
        webView.loadUrl("http://localhost:8080")
    }

    private fun enableKioskMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun startEmbeddedServer() {
        try {
            // Copia binary da assets
            val serverBinary = File(filesDir, "spotlive-server")
            if (!serverBinary.exists()) {
                assets.open("spotlive-server").use { input ->
                    serverBinary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                serverBinary.setExecutable(true)
            }

            // Avvia server
            val dataDir = filesDir.absolutePath
            serverProcess = ProcessBuilder(
                serverBinary.absolutePath,
                "--port", "8080",
                "--data", dataDir
            ).start()

            // Wait for server to start
            Thread.sleep(2000)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        serverProcess?.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Disabilita back button in kiosk mode
        // super.onBackPressed()
    }
}

// AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.spotlive.player">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="SpotLive Player"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Auto-start al boot -->
        <receiver
            android:name=".BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

## 🚀 BUILD & DEPLOY

### 1. Build Go Backend

```bash
cd backend
GOOS=android GOARCH=arm64 go build -o spotlive-server-arm64 cmd/main.go
# Copia in android/app/src/main/assets/spotlive-server
```

### 2. Build PWA Frontend

```bash
cd webapp
npm install
npm run build
# Output in webapp/dist/
# Copia in backend/cmd/webapp/dist/
```

### 3. Build APK

```bash
cd android
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## 📱 INSTALLAZIONE

```bash
# Via ADB
adb install app-release.apk

# O side-load manualmente
```

## ✅ CHECKLIST FINALE

- [ ] Completare componenti React mancanti
- [ ] Implementare scheduler completo
- [ ] Testare video playback
- [ ] Testare image viewer
- [ ] Configurare Android build.gradle
- [ ] Creare icona app
- [ ] Test su dispositivo reale
- [ ] Ottimizzazioni performance
- [ ] Documentazione utente

## 🎯 PRIORITÀ

1. **ALTA**: Completare Player.tsx con gestione playlist
2. **ALTA**: Implementare useScheduler hook
3. **MEDIA**: Aggiungere error handling robusto
4. **MEDIA**: Implementare retry logic per download
5. **BASSA**: UI setup wizard più bella
6. **BASSA**: Dashboard status (opzionale)
