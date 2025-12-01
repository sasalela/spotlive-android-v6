import React, { useEffect, useState, useCallback } from 'react';
import { VideoPlayer } from './VideoPlayer';
import { ImageViewer } from './ImageViewer';
import { api } from '../services/api';
import { storage } from '../services/storage';
import type { SchermoXml, Media } from '../types';

export const Player: React.FC = () => {
  const [schedule, setSchedule] = useState<SchermoXml | null>(null);
  const [playlist, setPlaylist] = useState<Media[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [currentMedia, setCurrentMedia] = useState<Media | null>(null);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState(true);

  // Load schedule
  useEffect(() => {
    loadSchedule();
  }, []);

  const loadSchedule = async () => {
    try {
      setLoading(true);
      setError('');

      // Fetch from server
      const result = await api.getSchedule();

      if (!result.success || !result.schedule) {
        throw new Error(result.error || 'Failed to load schedule');
      }

      const sched = result.schedule;
      setSchedule(sched);

      // Save to cache
      await storage.saveSchedule(sched);

      // Build playlist
      buildPlaylist(sched);

      // Download all media in background
      api.downloadAllMedia().catch(err => {
        console.error('Background download failed:', err);
      });

      setLoading(false);
    } catch (err: any) {
      console.error('Load schedule error:', err);
      setError(err.message);

      // Try to load from cache
      const cached = await storage.getSchedule();
      if (cached) {
        console.log('Using cached schedule');
        setSchedule(cached);
        buildPlaylist(cached);
        setLoading(false);
      } else {
        setLoading(false);
      }
    }
  };

  const buildPlaylist = (sched: SchermoXml) => {
    // Get all media from mediaFinestre
    const mediaList = sched.mediaFinestre
      .filter(mf => mf.media.attivo && mf.media.approvato)
      .sort((a, b) => a.ordine - b.ordine)
      .map(mf => mf.media);

    if (mediaList.length === 0) {
      setError('Nessun media nella playlist');
      return;
    }

    console.log(`Playlist built: ${mediaList.length} items`);
    setPlaylist(mediaList);
    setCurrentMedia(mediaList[0]);
    setCurrentIndex(0);
  };

  const nextMedia = useCallback(() => {
    if (!schedule || playlist.length === 0) return;

    let nextIdx: number;

    if (schedule.schermo.elenco) {
      // Sequential playback
      nextIdx = (currentIndex + 1) % playlist.length;
    } else {
      // Random playback
      nextIdx = Math.floor(Math.random() * playlist.length);

      // Avoid repeating same media
      if (playlist.length > 1 && nextIdx === currentIndex) {
        nextIdx = (nextIdx + 1) % playlist.length;
      }
    }

    console.log(`Next media: ${nextIdx + 1}/${playlist.length} - ${playlist[nextIdx].nome}`);
    setCurrentIndex(nextIdx);
    setCurrentMedia(playlist[nextIdx]);
  }, [schedule, playlist, currentIndex]);

  const handleMediaError = useCallback((errorMsg: string) => {
    console.error('Media playback error:', errorMsg);
    // Skip to next media on error
    setTimeout(nextMedia, 1000);
  }, [nextMedia]);

  // Reload schedule periodically (every 5 minutes)
  useEffect(() => {
    const interval = setInterval(() => {
      console.log('Periodic schedule reload...');
      loadSchedule();
    }, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, []);

  // Heartbeat (every 5 minutes)
  useEffect(() => {
    const interval = setInterval(() => {
      api.sendHeartbeat().catch(err => {
        console.error('Heartbeat failed:', err);
      });
    }, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div style={styles.loading}>
        <div style={styles.spinner}></div>
        <p style={styles.loadingText}>Caricamento programmazione...</p>
      </div>
    );
  }

  if (error && !currentMedia) {
    return (
      <div style={styles.error}>
        <h2>Errore</h2>
        <p>{error}</p>
        <button onClick={loadSchedule} style={styles.retryButton}>
          Riprova
        </button>
      </div>
    );
  }

  if (!currentMedia) {
    return (
      <div style={styles.error}>
        <p>Nessun media da riprodurre</p>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      {currentMedia.tipo === 'VIDEO' && currentMedia.video && (
        <VideoPlayer
          media={currentMedia}
          onEnded={nextMedia}
          onError={handleMediaError}
        />
      )}

      {currentMedia.tipo === 'IMAGE' && currentMedia.immagine && (
        <ImageViewer
          media={currentMedia}
          duration={currentMedia.tempo || 10}
          onEnded={nextMedia}
          onError={handleMediaError}
        />
      )}

      {/* Debug info (remove in production) */}
      {process.env.NODE_ENV === 'development' && (
        <div style={styles.debug}>
          <p>Media: {currentMedia.nome}</p>
          <p>Tipo: {currentMedia.tipo}</p>
          <p>Index: {currentIndex + 1}/{playlist.length}</p>
          <p>Mode: {schedule?.schermo.elenco ? 'Sequential' : 'Random'}</p>
        </div>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    width: '100vw',
    height: '100vh',
    backgroundColor: '#000',
    overflow: 'hidden',
    position: 'relative'
  },
  loading: {
    width: '100vw',
    height: '100vh',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#000',
    color: '#fff'
  },
  spinner: {
    width: '60px',
    height: '60px',
    border: '6px solid rgba(255, 255, 255, 0.1)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite'
  },
  loadingText: {
    marginTop: '20px',
    fontSize: '20px'
  },
  error: {
    width: '100vw',
    height: '100vh',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#1a1a1a',
    color: '#fff',
    padding: '40px',
    textAlign: 'center'
  },
  retryButton: {
    marginTop: '20px',
    padding: '12px 24px',
    fontSize: '16px',
    backgroundColor: '#228be6',
    color: '#fff',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer'
  },
  debug: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    color: '#fff',
    padding: '10px',
    fontSize: '12px',
    fontFamily: 'monospace'
  }
};
