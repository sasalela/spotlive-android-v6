import { openDB, DBSchema, IDBPDatabase } from 'idb';
import type { SchermoXml } from '../types';

interface SpotLiveDB extends DBSchema {
  media: {
    key: string;
    value: Blob;
  };
  schedule: {
    key: string;
    value: SchermoXml;
  };
  metadata: {
    key: string;
    value: any;
  };
}

class StorageService {
  private db: IDBPDatabase<SpotLiveDB> | null = null;
  private dbName = 'spotlive-cache';
  private version = 1;

  async init(): Promise<void> {
    if (this.db) return;

    this.db = await openDB<SpotLiveDB>(this.dbName, this.version, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('media')) {
          db.createObjectStore('media');
        }
        if (!db.objectStoreNames.contains('schedule')) {
          db.createObjectStore('schedule');
        }
        if (!db.objectStoreNames.contains('metadata')) {
          db.createObjectStore('metadata');
        }
      }
    });
  }

  async cacheMedia(filename: string, blob: Blob): Promise<void> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');
    await this.db.put('media', blob, filename);
  }

  async getMedia(filename: string): Promise<string | null> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');

    const blob = await this.db.get('media', filename);
    if (!blob) return null;

    return URL.createObjectURL(blob);
  }

  async hasMedia(filename: string): Promise<boolean> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');

    const blob = await this.db.get('media', filename);
    return !!blob;
  }

  async saveSchedule(schedule: SchermoXml): Promise<void> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');
    await this.db.put('schedule', schedule, 'current');
  }

  async getSchedule(): Promise<SchermoXml | null> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');

    const schedule = await this.db.get('schedule', 'current');
    return schedule || null;
  }

  async setMetadata(key: string, value: any): Promise<void> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');
    await this.db.put('metadata', value, key);
  }

  async getMetadata(key: string): Promise<any> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');
    return this.db.get('metadata', key);
  }

  async clearAll(): Promise<void> {
    await this.init();
    if (!this.db) throw new Error('Database not initialized');

    await this.db.clear('media');
    await this.db.clear('schedule');
    await this.db.clear('metadata');
  }

  async getStorageEstimate(): Promise<{ usage: number; quota: number }> {
    if ('storage' in navigator && 'estimate' in navigator.storage) {
      const estimate = await navigator.storage.estimate();
      return {
        usage: estimate.usage || 0,
        quota: estimate.quota || 0
      };
    }
    return { usage: 0, quota: 0 };
  }

  async requestPersistentStorage(): Promise<boolean> {
    if ('storage' in navigator && 'persist' in navigator.storage) {
      return navigator.storage.persist();
    }
    return false;
  }
}

export const storage = new StorageService();
