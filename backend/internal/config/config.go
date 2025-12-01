package config

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
)

// Config struttura configurazione applicazione
type Config struct {
	// Server HTTP
	ServerURL    string `json:"serverUrl"`    // http://80.88.90.214:80

	// Autenticazione
	Username     string `json:"username"`     // 01fcbgyvir
	Password     string `json:"password"`     // 01fcbgyvir (encrypted)
	IDMonitor    string `json:"idMonitor"`    // 567
	UserSchermo  string `json:"userSchermo"`  // cucciniello

	// FTP
	FTPServer    string `json:"ftpServer"`    // 80.88.90.214
	FTPPort      int    `json:"ftpPort"`      // 21
	FTPUsername  string `json:"ftpUsername"`  // ftptomcat
	FTPPassword  string `json:"ftpPassword"`  // LiveScreenSRL_2022 (encrypted)
	FTPDirectory string `json:"ftpDirectory"` // /

	// Configurazione player
	ConnectionMode   int    `json:"connectionMode"`   // 3 = PASV
	VideoQuality     int    `json:"videoQuality"`     // 34
	Delay            int    `json:"delay"`            // 20000 ms
	SecondiCache     int    `json:"secondiCache"`     // 5
	SecondiTolleranza int   `json:"secondiTolleranza"` // 12

	// Paths
	DataDir      string `json:"dataDir"`      // /data/data/com.spotlive.player/files
	MediaDir     string `json:"mediaDir"`     // dataDir/media
	CacheDir     string `json:"cacheDir"`     // dataDir/cache
}

var (
	currentConfig *Config
	configPath    string
	encryptionKey = []byte("spotlive2024key!") // 16 bytes for AES-128
)

// SetDataDir imposta directory dati (chiamato da Android)
func SetDataDir(dir string) {
	configPath = filepath.Join(dir, "config.json")
}

// Load carica configurazione da file
func Load() (*Config, error) {
	if configPath == "" {
		return nil, errors.New("data directory not set")
	}

	data, err := os.ReadFile(configPath)
	if err != nil {
		if os.IsNotExist(err) {
			// Config non esiste, ritorna default
			return GetDefault(), nil
		}
		return nil, err
	}

	// Decripta
	decrypted, err := decrypt(data)
	if err != nil {
		return nil, err
	}

	var cfg Config
	if err := json.Unmarshal(decrypted, &cfg); err != nil {
		return nil, err
	}

	currentConfig = &cfg
	return currentConfig, nil
}

// Save salva configurazione su file (encrypted)
func Save(cfg *Config) error {
	if configPath == "" {
		return errors.New("data directory not set")
	}

	// Assicura che le directory esistano
	cfg.MediaDir = filepath.Join(cfg.DataDir, "media")
	cfg.CacheDir = filepath.Join(cfg.DataDir, "cache")
	os.MkdirAll(cfg.MediaDir, 0755)
	os.MkdirAll(cfg.CacheDir, 0755)

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}

	// Cripta
	encrypted, err := encrypt(data)
	if err != nil {
		return err
	}

	// Salva
	if err := os.WriteFile(configPath, encrypted, 0600); err != nil {
		return err
	}

	currentConfig = cfg
	return nil
}

// Get ritorna configurazione corrente
func Get() *Config {
	if currentConfig == nil {
		currentConfig = GetDefault()
	}
	return currentConfig
}

// GetDefault ritorna configurazione di default
func GetDefault() *Config {
	return &Config{
		ServerURL:         "http://80.88.90.214:80",
		FTPServer:        "80.88.90.214",
		FTPPort:          21,
		FTPUsername:      "ftptomcat",
		FTPPassword:      "LiveScreenSRL_2022",
		FTPDirectory:     "/",
		ConnectionMode:   3,
		VideoQuality:     34,
		Delay:            20000,
		SecondiCache:     5,
		SecondiTolleranza: 12,
	}
}

// IsConfigured verifica se app è configurata
func IsConfigured() bool {
	cfg := Get()
	return cfg.Username != "" && cfg.Password != "" && cfg.IDMonitor != ""
}

// Encrypt/Decrypt helpers
func encrypt(plaintext []byte) ([]byte, error) {
	block, err := aes.NewCipher(encryptionKey)
	if err != nil {
		return nil, err
	}

	ciphertext := make([]byte, aes.BlockSize+len(plaintext))
	iv := ciphertext[:aes.BlockSize]
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		return nil, err
	}

	stream := cipher.NewCFBEncrypter(block, iv)
	stream.XORKeyStream(ciphertext[aes.BlockSize:], plaintext)

	return []byte(base64.StdEncoding.EncodeToString(ciphertext)), nil
}

func decrypt(ciphertext []byte) ([]byte, error) {
	decoded, err := base64.StdEncoding.DecodeString(string(ciphertext))
	if err != nil {
		return nil, err
	}

	block, err := aes.NewCipher(encryptionKey)
	if err != nil {
		return nil, err
	}

	if len(decoded) < aes.BlockSize {
		return nil, errors.New("ciphertext too short")
	}

	iv := decoded[:aes.BlockSize]
	decoded = decoded[aes.BlockSize:]

	stream := cipher.NewCFBDecrypter(block, iv)
	stream.XORKeyStream(decoded, decoded)

	return decoded, nil
}
