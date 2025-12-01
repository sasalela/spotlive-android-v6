import React, { useState } from 'react';
import { api } from '../services/api';
import type { Config } from '../types';

interface SetupWizardProps {
  onComplete: () => void;
}

export const SetupWizard: React.FC<SetupWizardProps> = ({ onComplete }) => {
  const [config, setConfig] = useState<Partial<Config>>({
    serverUrl: 'http://80.88.90.214:80',
    username: '',
    password: '',
    idMonitor: '',
    userSchermo: ''
  });

  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleTest = async () => {
    setTesting(true);
    setError('');
    setSuccess('');

    try {
      // Prima salva config temporanea
      await api.saveConfig(config);

      // Poi testa connessione
      const result = await api.testConnection();

      if (result.success) {
        setSuccess(`Connessione riuscita! Schermo: ${result.schermo || 'OK'}`);
      } else {
        setError(result.error || 'Test fallito');
      }
    } catch (e: any) {
      setError(e.message || 'Errore di connessione');
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    // Valida campi
    if (!config.username || !config.password || !config.idMonitor) {
      setError('Username, Password e ID Monitor sono obbligatori');
      return;
    }

    setSaving(true);
    setError('');

    try {
      await api.saveConfig(config);
      setSuccess('Configurazione salvata!');

      // Attendi 1 secondo poi chiama onComplete
      setTimeout(() => {
        onComplete();
      }, 1000);
    } catch (e: any) {
      setError(e.message || 'Errore salvataggio');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h1 style={styles.title}>SpotLiveScreen Setup</h1>
        <p style={styles.subtitle}>Configura il player per iniziare</p>

        <div style={styles.form}>
          <div style={styles.field}>
            <label style={styles.label}>Server URL:</label>
            <input
              type="text"
              value={config.serverUrl}
              onChange={e => setConfig({ ...config, serverUrl: e.target.value })}
              style={styles.input}
              placeholder="http://80.88.90.214:80"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Username: *</label>
            <input
              type="text"
              value={config.username}
              onChange={e => setConfig({ ...config, username: e.target.value })}
              style={styles.input}
              placeholder="es. 01fcbgyvir"
              autoComplete="username"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Password: *</label>
            <input
              type="password"
              value={config.password}
              onChange={e => setConfig({ ...config, password: e.target.value })}
              style={styles.input}
              autoComplete="current-password"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>ID Monitor: *</label>
            <input
              type="text"
              value={config.idMonitor}
              onChange={e => setConfig({ ...config, idMonitor: e.target.value })}
              style={styles.input}
              placeholder="es. 567"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>User Schermo (opzionale):</label>
            <input
              type="text"
              value={config.userSchermo}
              onChange={e => setConfig({ ...config, userSchermo: e.target.value })}
              style={styles.input}
              placeholder="es. cucciniello"
            />
          </div>

          {error && <div style={styles.error}>{error}</div>}
          {success && <div style={styles.success}>{success}</div>}

          <div style={styles.buttons}>
            <button
              onClick={handleTest}
              disabled={testing || saving}
              style={{ ...styles.button, ...styles.buttonSecondary }}
            >
              {testing ? 'Testing...' : 'Test Connessione'}
            </button>

            <button
              onClick={handleSave}
              disabled={testing || saving}
              style={{ ...styles.button, ...styles.buttonPrimary }}
            >
              {saving ? 'Salvataggio...' : 'Salva e Avvia'}
            </button>
          </div>

          <p style={styles.hint}>* Campi obbligatori</p>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    backgroundColor: '#1a1a1a',
    padding: '20px'
  },
  card: {
    backgroundColor: '#2a2a2a',
    borderRadius: '12px',
    padding: '40px',
    maxWidth: '500px',
    width: '100%',
    boxShadow: '0 4px 6px rgba(0, 0, 0, 0.3)'
  },
  title: {
    color: '#ffffff',
    fontSize: '28px',
    marginBottom: '8px',
    textAlign: 'center'
  },
  subtitle: {
    color: '#aaaaaa',
    fontSize: '14px',
    marginBottom: '30px',
    textAlign: 'center'
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px'
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px'
  },
  label: {
    color: '#ffffff',
    fontSize: '14px',
    fontWeight: '500'
  },
  input: {
    padding: '12px',
    fontSize: '16px',
    borderRadius: '6px',
    border: '1px solid #444',
    backgroundColor: '#1a1a1a',
    color: '#ffffff',
    outline: 'none',
    transition: 'border-color 0.2s'
  },
  error: {
    color: '#ff6b6b',
    fontSize: '14px',
    padding: '12px',
    backgroundColor: 'rgba(255, 107, 107, 0.1)',
    borderRadius: '6px',
    border: '1px solid rgba(255, 107, 107, 0.3)'
  },
  success: {
    color: '#51cf66',
    fontSize: '14px',
    padding: '12px',
    backgroundColor: 'rgba(81, 207, 102, 0.1)',
    borderRadius: '6px',
    border: '1px solid rgba(81, 207, 102, 0.3)'
  },
  buttons: {
    display: 'flex',
    gap: '12px',
    marginTop: '10px'
  },
  button: {
    flex: 1,
    padding: '14px',
    fontSize: '16px',
    fontWeight: '600',
    borderRadius: '6px',
    border: 'none',
    cursor: 'pointer',
    transition: 'all 0.2s',
    outline: 'none'
  },
  buttonPrimary: {
    backgroundColor: '#228be6',
    color: '#ffffff'
  },
  buttonSecondary: {
    backgroundColor: '#444',
    color: '#ffffff'
  },
  hint: {
    color: '#888',
    fontSize: '12px',
    marginTop: '-10px',
    textAlign: 'center'
  }
};
