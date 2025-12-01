package xml

import (
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"spotlive-server/internal/config"
	"time"
)

// SchermoXml root structure
type SchermoXml struct {
	XMLName          xml.Name          `xml:"it.zerounorabbit.spotlivescreen.SchermoXml"`
	Schermo          Schermo           `xml:"schermo"`
	MediaFinestre    []MediaFinestra   `xml:"mediaFinestre>it.zerounorabbit.spotlivescreen.MediaFinestra"`
	Programmazioni   interface{}       `xml:"programmazioni"`
	Media            []Media           `xml:"media>it.zerounorabbit.spotlivescreen.Media"`
}

// Schermo configurazione display
type Schermo struct {
	ID                      int                `xml:"id"`
	Nome                    string             `xml:"nome"`
	Indirizzo               string             `xml:"indirizzo"`
	Larghezza               int                `xml:"larghezza"`
	Altezza                 int                `xml:"altezza"`
	Attivo                  bool               `xml:"attivo"`
	Finestre                []Finestra         `xml:"finestre>it.zerounorabbit.spotlivescreen.Finestra"`
	Orari                   []Orario           `xml:"orari>it.zerounorabbit.spotlivescreen.Orario"`
	CategoriaMerceologica   Categoria          `xml:"categoriaMerceologica"`
	Lun                     bool               `xml:"lun"`
	Mar                     bool               `xml:"mar"`
	Mer                     bool               `xml:"mer"`
	Gio                     bool               `xml:"gio"`
	Ven                     bool               `xml:"ven"`
	Sab                     bool               `xml:"sab"`
	Dom                     bool               `xml:"dom"`
	Elenco                  bool               `xml:"elenco"`
	OraDownload01           string             `xml:"oraDownload01"`
	OraDownload02           string             `xml:"oraDownload02"`
	OraDownload03           string             `xml:"oraDownload03"`
	OraDownload04           string             `xml:"oraDownload04"`
	OraDownload05           string             `xml:"oraDownload05"`
	OraDownload06           string             `xml:"oraDownload06"`
	OraDownload07           string             `xml:"oraDownload07"`
	OraDownload08           string             `xml:"oraDownload08"`
	OraRestart              string             `xml:"oraRestart"`
}

// Finestra zona dello schermo
type Finestra struct {
	ID              int    `xml:"id"`
	Nome            string `xml:"nome"`
	Altezza         int    `xml:"altezza"`
	Larghezza       int    `xml:"larghezza"`
	Alto            int    `xml:"alto"`
	Destra          int    `xml:"destra"`
	Attiva          bool   `xml:"attiva"`
	Spot            bool   `xml:"spot"`
	ImgNoInternet   string `xml:"imgNoInternet"`
	Audio           bool   `xml:"audio"`
}

// Orario fascia oraria
type Orario struct {
	ID         int    `xml:"id"`
	OraInizio  string `xml:"oraInizio"`
	OraFine    string `xml:"oraFine"`
	Crediti    int    `xml:"crediti"`
}

// MediaFinestra associazione media-finestra
type MediaFinestra struct {
	ID        int    `xml:"id"`
	Tipo      string `xml:"tipo"`
	Ordine    int    `xml:"ordine"`
	Media     Media  `xml:"media"`
}

// Media contenuto multimediale
type Media struct {
	ID             int       `xml:"id"`
	Nome           string    `xml:"nome"`
	Attivo         bool      `xml:"attivo"`
	Tipo           string    `xml:"tipo"`
	Video          string    `xml:"video"`
	Immagine       string    `xml:"immagine"`
	Audio          string    `xml:"audio"`
	Miniatura      string    `xml:"miniatura"`
	Pubblico       bool      `xml:"pubblico"`
	Tempo          int       `xml:"tempo"`
	NumeroNotizie  int       `xml:"numeroNotizie"`
	Approvato      bool      `xml:"approvato"`
	Crediti        int       `xml:"crediti"`
	Categoria      Categoria `xml:"categoria"`
}

// Categoria categoria merceologica
type Categoria struct {
	ID   int    `xml:"id"`
	Nome string `xml:"nome"`
}

// FetchSchedule scarica programmazione dal server
func FetchSchedule() (*SchermoXml, error) {
	cfg := config.Get()

	// Costruisce URL (identico al vecchio client)
	baseURL := cfg.ServerURL + "/spotlivescreen/XmlServlet"
	params := url.Values{}
	params.Add("version", "600")
	params.Add("idSchermo", cfg.IDMonitor)
	if cfg.UserSchermo != "" {
		params.Add("userschermo", cfg.UserSchermo)
	}

	fullURL := baseURL + "?" + params.Encode()

	// HTTP GET
	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := http.Get(fullURL)
	if err != nil {
		return nil, fmt.Errorf("HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP error: %d", resp.StatusCode)
	}

	// Legge body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read body failed: %w", err)
	}

	// Parse XML
	var schedule SchermoXml
	if err := xml.Unmarshal(body, &schedule); err != nil {
		return nil, fmt.Errorf("XML parse failed: %w", err)
	}

	return &schedule, nil
}

// SendUpdate invia heartbeat al server
func SendUpdate() error {
	cfg := config.Get()

	baseURL := cfg.ServerURL + "/spotlivescreen/XmlServlet"
	params := url.Values{}
	params.Add("version", "600")
	params.Add("update", "1")
	params.Add("idSchermo", cfg.IDMonitor)
	if cfg.UserSchermo != "" {
		params.Add("userschermo", cfg.UserSchermo)
	}

	fullURL := baseURL + "?" + params.Encode()

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := http.Get(fullURL)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return nil
}

// GetDownloadSchedule ritorna orari download
func (s *SchermoXml) GetDownloadSchedule() []string {
	orari := []string{
		s.Schermo.OraDownload01,
		s.Schermo.OraDownload02,
		s.Schermo.OraDownload03,
		s.Schermo.OraDownload04,
		s.Schermo.OraDownload05,
		s.Schermo.OraDownload06,
		s.Schermo.OraDownload07,
		s.Schermo.OraDownload08,
	}

	var result []string
	for _, ora := range orari {
		if ora != "" && ora != "00:00:00" {
			result = append(result, ora)
		}
	}
	return result
}

// GetMediaFiles ritorna lista file media da scaricare
func (s *SchermoXml) GetMediaFiles() []string {
	var files []string
	seen := make(map[string]bool)

	for _, mf := range s.MediaFinestre {
		if mf.Media.Video != "" && !seen[mf.Media.Video] {
			files = append(files, mf.Media.Video)
			seen[mf.Media.Video] = true
		}
		if mf.Media.Immagine != "" && !seen[mf.Media.Immagine] {
			files = append(files, mf.Media.Immagine)
			seen[mf.Media.Immagine] = true
		}
		if mf.Media.Audio != "" && !seen[mf.Media.Audio] {
			files = append(files, mf.Media.Audio)
			seen[mf.Media.Audio] = true
		}
	}

	// Aggiungi immagine fallback
	for _, finestra := range s.Schermo.Finestre {
		if finestra.ImgNoInternet != "" && !seen[finestra.ImgNoInternet] {
			files = append(files, finestra.ImgNoInternet)
			seen[finestra.ImgNoInternet] = true
		}
	}

	return files
}
