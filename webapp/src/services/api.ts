import type {
  Config,
  ConfigResponse,
  ScheduleResponse,
  DownloadResponse,
  StatusResponse,
  TestConnectionResponse
} from '../types';

class APIService {
  private baseUrl = '/api';

  async getConfig(): Promise<ConfigResponse> {
    const response = await fetch(`${this.baseUrl}/config`);
    if (!response.ok) throw new Error('Failed to fetch config');
    return response.json();
  }

  async saveConfig(config: Partial<Config>): Promise<{ success: boolean }> {
    const response = await fetch(`${this.baseUrl}/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    if (!response.ok) throw new Error('Failed to save config');
    return response.json();
  }

  async testConnection(): Promise<TestConnectionResponse> {
    const response = await fetch(`${this.baseUrl}/config/test`, {
      method: 'POST'
    });
    if (!response.ok) throw new Error('Connection test failed');
    return response.json();
  }

  async getSchedule(): Promise<ScheduleResponse> {
    const response = await fetch(`${this.baseUrl}/schedule`);
    if (!response.ok) throw new Error('Failed to fetch schedule');
    return response.json();
  }

  async downloadAllMedia(): Promise<DownloadResponse> {
    const response = await fetch(`${this.baseUrl}/media/download-all`, {
      method: 'POST'
    });
    if (!response.ok) throw new Error('Failed to download media');
    return response.json();
  }

  async sendHeartbeat(): Promise<{ success: boolean }> {
    const response = await fetch(`${this.baseUrl}/heartbeat`, {
      method: 'POST'
    });
    if (!response.ok) throw new Error('Failed to send heartbeat');
    return response.json();
  }

  async getStatus(): Promise<StatusResponse> {
    const response = await fetch(`${this.baseUrl}/status`);
    if (!response.ok) throw new Error('Failed to fetch status');
    return response.json();
  }

  getMediaUrl(filename: string): string {
    // Remove "upload/" prefix if present
    const cleanFilename = filename.replace(/^upload\//, '');
    return `${this.baseUrl}/media/upload/${encodeURIComponent(cleanFilename)}`;
  }

  getCachedMediaUrl(filename: string): string {
    const cleanFilename = filename.replace(/^upload\//, '');
    return `${this.baseUrl}/cache/${encodeURIComponent(cleanFilename)}`;
  }
}

export const api = new APIService();
