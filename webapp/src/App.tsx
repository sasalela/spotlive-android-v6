import React, { useEffect, useState } from 'react';
import { SetupWizard } from './components/SetupWizard';
import { Player } from './components/Player';
import { api } from './services/api';
import { storage } from './services/storage';

export const App: React.FC = () => {
  const [configured, setConfigured] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    checkConfiguration();
    initStorage();
  }, []);

  const checkConfiguration = async () => {
    try {
      const result = await api.getConfig();
      setConfigured(result.configured);
    } catch (err: any) {
      console.error('Failed to check configuration:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const initStorage = async () => {
    try {
      await storage.init();
      // Request persistent storage
      const granted = await storage.requestPersistentStorage();
      console.log('Persistent storage granted:', granted);

      // Log storage estimate
      const estimate = await storage.getStorageEstimate();
      console.log('Storage:', {
        used: `${(estimate.usage / 1024 / 1024).toFixed(2)} MB`,
        quota: `${(estimate.quota / 1024 / 1024).toFixed(2)} MB`,
        percent: `${((estimate.usage / estimate.quota) * 100).toFixed(1)}%`
      });
    } catch (err) {
      console.error('Storage init failed:', err);
    }
  };

  const handleSetupComplete = () => {
    setConfigured(true);
  };

  if (loading) {
    return (
      <div style={styles.loading}>
        <div style={styles.spinner}></div>
        <p style={styles.loadingText}>SpotLiveScreen Player v6.0</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={styles.error}>
        <h2>Errore</h2>
        <p>{error}</p>
        <button onClick={() => window.location.reload()} style={styles.button}>
          Ricarica
        </button>
      </div>
    );
  }

  if (!configured) {
    return <SetupWizard onComplete={handleSetupComplete} />;
  }

  return <Player />;
};

const styles: Record<string, React.CSSProperties> = {
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
    fontSize: '18px'
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
  button: {
    marginTop: '20px',
    padding: '12px 24px',
    fontSize: '16px',
    backgroundColor: '#228be6',
    color: '#fff',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer'
  }
};
