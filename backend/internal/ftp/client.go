package ftp

import (
	"fmt"
	"io"
	"spotlive-server/internal/config"
	"time"

	"github.com/jlaffaye/ftp"
)

// Client wrapper per FTP
type Client struct {
	conn *ftp.ServerConn
	cfg  *config.Config
}

// NewClient crea nuovo client FTP
func NewClient() *Client {
	return &Client{
		cfg: config.Get(),
	}
}

// Connect connette al server FTP
func (c *Client) Connect() error {
	addr := fmt.Sprintf("%s:%d", c.cfg.FTPServer, c.cfg.FTPPort)

	conn, err := ftp.Dial(addr, ftp.DialWithTimeout(10*time.Second))
	if err != nil {
		return fmt.Errorf("FTP dial failed: %w", err)
	}

	if err := conn.Login(c.cfg.FTPUsername, c.cfg.FTPPassword); err != nil {
		conn.Quit()
		return fmt.Errorf("FTP login failed: %w", err)
	}

	c.conn = conn
	return nil
}

// Download scarica file dal server FTP
func (c *Client) Download(remotePath string) (io.ReadCloser, error) {
	if c.conn == nil {
		if err := c.Connect(); err != nil {
			return nil, err
		}
	}

	// Change directory se specificato
	if c.cfg.FTPDirectory != "/" && c.cfg.FTPDirectory != "" {
		if err := c.conn.ChangeDir(c.cfg.FTPDirectory); err != nil {
			return nil, fmt.Errorf("FTP chdir failed: %w", err)
		}
	}

	// Download file
	response, err := c.conn.Retr(remotePath)
	if err != nil {
		return nil, fmt.Errorf("FTP download failed: %w", err)
	}

	return response, nil
}

// List elenca files in directory
func (c *Client) List(path string) ([]string, error) {
	if c.conn == nil {
		if err := c.Connect(); err != nil {
			return nil, err
		}
	}

	entries, err := c.conn.List(path)
	if err != nil {
		return nil, fmt.Errorf("FTP list failed: %w", err)
	}

	var files []string
	for _, entry := range entries {
		if entry.Type == ftp.EntryTypeFile {
			files = append(files, entry.Name)
		}
	}

	return files, nil
}

// Close chiude connessione
func (c *Client) Close() error {
	if c.conn != nil {
		return c.conn.Quit()
	}
	return nil
}
