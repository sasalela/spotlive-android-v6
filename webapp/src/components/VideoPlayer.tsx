import React, { useEffect, useRef, useState } from 'react';
import { api } from '../services/api';
import type { Media } from '../types';

interface VideoPlayerProps {
  media: Media;
  onEnded: () => void;
  onError: (error: string) => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ media, onEnded, onError }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [videoUrl, setVideoUrl] = useState<string>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!media.video) {
      onError('No video file specified');
      return;
    }

    setLoading(true);
    const url = api.getMediaUrl(media.video);
    setVideoUrl(url);

    // Preload video
    const video = videoRef.current;
    if (video) {
      video.load();
    }
  }, [media, onError]);

  const handleCanPlay = () => {
    setLoading(false);
    console.log(`Video ready to play: ${media.nome}`);
  };

  const handleError = (e: React.SyntheticEvent<HTMLVideoElement, Event>) => {
    const video = e.currentTarget;
    const errorMessage = video.error
      ? `Video error: ${video.error.message} (code: ${video.error.code})`
      : 'Unknown video error';

    console.error(errorMessage, media);
    onError(errorMessage);
  };

  const handleEnded = () => {
    console.log(`Video ended: ${media.nome}`);
    onEnded();
  };

  return (
    <div style={styles.container}>
      {loading && (
        <div style={styles.loading}>
          <div style={styles.spinner}></div>
          <p style={styles.loadingText}>Caricamento video...</p>
        </div>
      )}

      <video
        ref={videoRef}
        src={videoUrl}
        autoPlay
        muted={!media.audio}
        playsInline
        onCanPlay={handleCanPlay}
        onError={handleError}
        onEnded={handleEnded}
        style={styles.video}
      />
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    position: 'relative',
    width: '100vw',
    height: '100vh',
    backgroundColor: '#000',
    overflow: 'hidden'
  },
  video: {
    width: '100%',
    height: '100%',
    objectFit: 'cover'
  },
  loading: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#000',
    zIndex: 10
  },
  spinner: {
    width: '50px',
    height: '50px',
    border: '5px solid rgba(255, 255, 255, 0.1)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite'
  },
  loadingText: {
    color: '#fff',
    marginTop: '20px',
    fontSize: '18px'
  }
};

// Add CSS animation in index.css
