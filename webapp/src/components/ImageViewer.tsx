import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import type { Media } from '../types';

interface ImageViewerProps {
  media: Media;
  duration: number;
  onEnded: () => void;
  onError: (error: string) => void;
}

export const ImageViewer: React.FC<ImageViewerProps> = ({
  media,
  duration,
  onEnded,
  onError
}) => {
  const [imageUrl, setImageUrl] = useState<string>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!media.immagine) {
      onError('No image file specified');
      return;
    }

    const url = api.getMediaUrl(media.immagine);
    setImageUrl(url);

    // Timer for auto-advance
    const timer = setTimeout(() => {
      console.log(`Image duration ended: ${media.nome}`);
      onEnded();
    }, duration * 1000);

    return () => clearTimeout(timer);
  }, [media, duration, onEnded, onError]);

  const handleLoad = () => {
    setLoading(false);
    console.log(`Image loaded: ${media.nome}`);
  };

  const handleError = () => {
    console.error(`Image load error: ${media.nome}`);
    onError('Failed to load image');
  };

  return (
    <div style={styles.container}>
      {loading && (
        <div style={styles.loading}>
          <div style={styles.spinner}></div>
          <p style={styles.loadingText}>Caricamento immagine...</p>
        </div>
      )}

      {imageUrl && (
        <img
          src={imageUrl}
          alt={media.nome}
          onLoad={handleLoad}
          onError={handleError}
          style={styles.image}
        />
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    position: 'relative',
    width: '100vw',
    height: '100vh',
    backgroundColor: '#000',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden'
  },
  image: {
    maxWidth: '100%',
    maxHeight: '100%',
    objectFit: 'contain'
  },
  loading: {
    position: 'absolute',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center'
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
