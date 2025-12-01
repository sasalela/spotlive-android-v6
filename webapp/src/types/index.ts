// Configuration
export interface Config {
  serverUrl: string;
  username: string;
  password: string;
  idMonitor: string;
  userSchermo?: string;
  ftpServer?: string;
  ftpPort?: number;
  ftpUsername?: string;
  ftpPassword?: string;
}

export interface ConfigResponse {
  configured: boolean;
  config?: Config;
}

// Schedule structures
export interface Schermo {
  id: number;
  nome: string;
  indirizzo: string;
  larghezza: number;
  altezza: number;
  attivo: boolean;
  finestre: Finestra[];
  orari: Orario[];
  categoriaMerceologica: Categoria;
  lun: boolean;
  mar: boolean;
  mer: boolean;
  gio: boolean;
  ven: boolean;
  sab: boolean;
  dom: boolean;
  elenco: boolean;
  oraDownload01: string;
  oraDownload02: string;
  oraDownload03: string;
  oraDownload04: string;
  oraDownload05: string;
  oraDownload06: string;
  oraDownload07: string;
  oraDownload08: string;
  oraRestart: string;
}

export interface Finestra {
  id: number;
  nome: string;
  altezza: number;
  larghezza: number;
  alto: number;
  destra: number;
  attiva: boolean;
  spot: boolean;
  imgNoInternet: string;
  audio: boolean;
}

export interface Orario {
  id: number;
  oraInizio: string;
  oraFine: string;
  crediti: number;
}

export interface MediaFinestra {
  id: number;
  tipo: MediaType;
  ordine: number;
  media: Media;
}

export interface Media {
  id: number;
  nome: string;
  attivo: boolean;
  tipo: MediaType;
  video?: string;
  immagine?: string;
  audio?: string;
  miniatura?: string;
  pubblico: boolean;
  tempo: number;
  numeroNotizie: number;
  approvato: boolean;
  crediti: number;
  categoria: Categoria;
}

export interface Categoria {
  id: number;
  nome: string;
}

export interface SchermoXml {
  schermo: Schermo;
  mediaFinestre: MediaFinestra[];
  programmazioni: any;
  media: Media[];
}

export type MediaType = 'VIDEO' | 'IMAGE' | 'RSS' | 'WEB' | 'YOUTUBE';

// API Responses
export interface ScheduleResponse {
  success: boolean;
  schedule?: SchermoXml;
  error?: string;
}

export interface DownloadResponse {
  success: boolean;
  message?: string;
  error?: string;
}

export interface StatusResponse {
  configured: boolean;
  idMonitor: string;
  mediaCount: number;
  version: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message?: string;
  schermo?: string;
  error?: string;
}
