package server

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"spotlive-server/internal/config"
	"spotlive-server/internal/ftp"
	"spotlive-server/internal/xml"

	"github.com/gin-gonic/gin"
)

// ConfigRequest struttura richiesta configurazione
type ConfigRequest struct {
	ServerURL    string `json:"serverUrl"`
	Username     string `json:"username"`
	Password     string `json:"password"`
	IDMonitor    string `json:"idMonitor"`
	UserSchermo  string `json:"userSchermo"`
}

// ConfigResponse struttura risposta configurazione
type ConfigResponse struct {
	Configured bool   `json:"configured"`
	Config     *config.Config `json:"config,omitempty"`
}

// ScheduleResponse struttura risposta programmazione
type ScheduleResponse struct {
	Success  bool            `json:"success"`
	Schedule *xml.SchermoXml `json:"schedule,omitempty"`
	Error    string          `json:"error,omitempty"`
}

// DownloadResponse struttura risposta download
type DownloadResponse struct {
	Success bool   `json:"success"`
	Message string `json:"message"`
	Error   string `json:"error,omitempty"`
}

// GetConfig ritorna configurazione corrente
func GetConfig(c *gin.Context) {
	cfg := config.Get()
	isConfigured := config.IsConfigured()

	response := ConfigResponse{
		Configured: isConfigured,
	}

	if isConfigured {
		// Non esporre password
		safeCfg := *cfg
		safeCfg.Password = "***"
		safeCfg.FTPPassword = "***"
		response.Config = &safeCfg
	}

	c.JSON(200, response)
}

// SaveConfig salva nuova configurazione
func SaveConfig(c *gin.Context) {
	var req ConfigRequest
	if err := c.BindJSON(&req); err != nil {
		c.JSON(400, gin.H{"error": "Invalid request"})
		return
	}

	// Valida campi obbligatori
	if req.Username == "" || req.Password == "" || req.IDMonitor == "" {
		c.JSON(400, gin.H{"error": "Username, password and idMonitor are required"})
		return
	}

	// Crea nuova config
	cfg := config.GetDefault()
	if req.ServerURL != "" {
		cfg.ServerURL = req.ServerURL
	}
	cfg.Username = req.Username
	cfg.Password = req.Password
	cfg.IDMonitor = req.IDMonitor
	cfg.UserSchermo = req.UserSchermo

	// Salva
	if err := config.Save(cfg); err != nil {
		c.JSON(500, gin.H{"error": fmt.Sprintf("Failed to save config: %v", err)})
		return
	}

	c.JSON(200, gin.H{"success": true, "message": "Configuration saved"})
}

// TestConnection testa connessione al server
func TestConnection(c *gin.Context) {
	// Prova a scaricare programmazione
	schedule, err := xml.FetchSchedule()
	if err != nil {
		c.JSON(500, gin.H{
			"success": false,
			"error":   fmt.Sprintf("Connection test failed: %v", err),
		})
		return
	}

	c.JSON(200, gin.H{
		"success": true,
		"message": "Connection successful",
		"schermo": schedule.Schermo.Nome,
	})
}

// GetSchedule scarica programmazione dal server
func GetSchedule(c *gin.Context) {
	schedule, err := xml.FetchSchedule()
	if err != nil {
		c.JSON(500, ScheduleResponse{
			Success: false,
			Error:   err.Error(),
		})
		return
	}

	c.JSON(200, ScheduleResponse{
		Success:  true,
		Schedule: schedule,
	})
}

// DownloadMedia scarica file media via FTP proxy
func DownloadMedia(c *gin.Context) {
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(400, gin.H{"error": "Filename required"})
		return
	}

	// Path relativo (es: "upload/video.mp4")
	remotePath := filename
	if filepath.Dir(filename) == "." {
		remotePath = "upload/" + filename
	}

	// Connetti FTP
	ftpClient := ftp.NewClient()
	defer ftpClient.Close()

	if err := ftpClient.Connect(); err != nil {
		c.JSON(500, gin.H{"error": fmt.Sprintf("FTP connection failed: %v", err)})
		return
	}

	// Download
	stream, err := ftpClient.Download(remotePath)
	if err != nil {
		c.JSON(500, gin.H{"error": fmt.Sprintf("FTP download failed: %v", err)})
		return
	}
	defer stream.Close()

	// Determina content type
	contentType := "application/octet-stream"
	ext := filepath.Ext(filename)
	switch ext {
	case ".mp4":
		contentType = "video/mp4"
	case ".jpg", ".jpeg":
		contentType = "image/jpeg"
	case ".png":
		contentType = "image/png"
	case ".gif":
		contentType = "image/gif"
	}

	// Stream al client
	c.Header("Content-Type", contentType)
	c.Header("Content-Disposition", fmt.Sprintf("inline; filename=\"%s\"", filepath.Base(filename)))
	c.Status(200)

	// Copia stream
	if _, err := io.Copy(c.Writer, stream); err != nil {
		// Errore già dopo headers inviati, non possiamo più rispondere con JSON
		fmt.Printf("Error streaming file: %v\n", err)
	}
}

// CacheMedia salva media in cache locale
func CacheMedia(c *gin.Context) {
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(400, gin.H{"error": "Filename required"})
		return
	}

	cfg := config.Get()
	cachePath := filepath.Join(cfg.CacheDir, filepath.Base(filename))

	// Ricevi file dal client
	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(400, gin.H{"error": "File required"})
		return
	}

	// Salva
	if err := c.SaveUploadedFile(file, cachePath); err != nil {
		c.JSON(500, gin.H{"error": fmt.Sprintf("Failed to save file: %v", err)})
		return
	}

	c.JSON(200, gin.H{"success": true, "path": cachePath})
}

// GetCachedMedia ritorna media dalla cache
func GetCachedMedia(c *gin.Context) {
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(400, gin.H{"error": "Filename required"})
		return
	}

	cfg := config.Get()
	cachePath := filepath.Join(cfg.CacheDir, filepath.Base(filename))

	// Verifica esistenza
	if _, err := os.Stat(cachePath); os.IsNotExist(err) {
		c.JSON(404, gin.H{"error": "File not found in cache"})
		return
	}

	c.File(cachePath)
}

// DownloadAllMedia scarica tutti i media della programmazione
func DownloadAllMedia(c *gin.Context) {
	// Scarica programmazione
	schedule, err := xml.FetchSchedule()
	if err != nil {
		c.JSON(500, DownloadResponse{
			Success: false,
			Error:   fmt.Sprintf("Failed to fetch schedule: %v", err),
		})
		return
	}

	// Lista files da scaricare
	files := schedule.GetMediaFiles()

	// Connetti FTP
	ftpClient := ftp.NewClient()
	defer ftpClient.Close()

	if err := ftpClient.Connect(); err != nil {
		c.JSON(500, DownloadResponse{
			Success: false,
			Error:   fmt.Sprintf("FTP connection failed: %v", err),
		})
		return
	}

	cfg := config.Get()
	downloaded := 0
	skipped := 0
	errors := 0

	for _, filePathRel := range files {
		// Path locale
		localPath := filepath.Join(cfg.MediaDir, filepath.Base(filePathRel))

		// Salta se già esiste
		if _, err := os.Stat(localPath); err == nil {
			skipped++
			continue
		}

		// Download
		stream, err := ftpClient.Download(filePathRel)
		if err != nil {
			fmt.Printf("Error downloading %s: %v\n", filePathRel, err)
			errors++
			continue
		}

		// Salva localmente
		outFile, err := os.Create(localPath)
		if err != nil {
			stream.Close()
			errors++
			continue
		}

		_, err = io.Copy(outFile, stream)
		outFile.Close()
		stream.Close()

		if err != nil {
			errors++
			continue
		}

		downloaded++
	}

	c.JSON(200, DownloadResponse{
		Success: true,
		Message: fmt.Sprintf("Downloaded: %d, Skipped: %d, Errors: %d", downloaded, skipped, errors),
	})
}

// SendHeartbeat invia update al server
func SendHeartbeat(c *gin.Context) {
	if err := xml.SendUpdate(); err != nil {
		c.JSON(500, gin.H{"error": err.Error()})
		return
	}

	c.JSON(200, gin.H{"success": true})
}

// GetStatus ritorna status del player
func GetStatus(c *gin.Context) {
	cfg := config.Get()

	// Conta file in cache
	mediaFiles, _ := os.ReadDir(cfg.MediaDir)

	c.JSON(200, gin.H{
		"configured":  config.IsConfigured(),
		"idMonitor":   cfg.IDMonitor,
		"mediaCount":  len(mediaFiles),
		"version":     "6.0.0",
	})
}
