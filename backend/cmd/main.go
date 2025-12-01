package main

import (
	"embed"
	"flag"
	"fmt"
	"io/fs"
	"log"
	"net/http"
	"spotlive-server/internal/config"
	"spotlive-server/internal/server"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
)

//go:embed webapp/dist
var staticFiles embed.FS

func main() {
	// Flags
	port := flag.String("port", "8080", "Server port")
	dataDir := flag.String("data", "/data/data/com.spotlive.player/files", "Data directory")
	debug := flag.Bool("debug", false, "Debug mode")
	flag.Parse()

	// Imposta data directory
	config.SetDataDir(*dataDir)

	// Carica configurazione
	if _, err := config.Load(); err != nil {
		log.Printf("Warning: failed to load config: %v (using defaults)", err)
	}

	// Gin mode
	if !*debug {
		gin.SetMode(gin.ReleaseMode)
	}

	// Router
	r := gin.Default()

	// CORS (permetti richieste da WebView)
	r.Use(cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Type", "Authorization"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: true,
	}))

	// API routes
	api := r.Group("/api")
	{
		// Config
		api.GET("/config", server.GetConfig)
		api.POST("/config", server.SaveConfig)
		api.POST("/config/test", server.TestConnection)

		// Schedule
		api.GET("/schedule", server.GetSchedule)

		// Media
		api.GET("/media/:filename", server.DownloadMedia)
		api.GET("/cache/:filename", server.GetCachedMedia)
		api.POST("/cache/:filename", server.CacheMedia)
		api.POST("/media/download-all", server.DownloadAllMedia)

		// Status
		api.GET("/status", server.GetStatus)
		api.POST("/heartbeat", server.SendHeartbeat)
	}

	// Serve PWA statica
	staticFS, err := fs.Sub(staticFiles, "webapp/dist")
	if err != nil {
		log.Fatal(err)
	}
	r.NoRoute(func(c *gin.Context) {
		c.FileFromFS(c.Request.URL.Path, http.FS(staticFS))
	})

	// Start server
	addr := fmt.Sprintf(":%s", *port)
	log.Printf("SpotLiveScreen Server v6.0")
	log.Printf("Listening on http://localhost%s", addr)
	log.Printf("Data directory: %s", *dataDir)

	if err := r.Run(addr); err != nil {
		log.Fatal(err)
	}
}
