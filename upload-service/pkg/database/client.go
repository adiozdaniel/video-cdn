package database

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	_ "github.com/lib/pq"
)

// NewClient creates a new PostgreSQL database connection with optimized pooling
func NewClient() *sql.DB {
	host := getEnv("DB_HOST", "postgres")
	port := getEnv("DB_PORT", "5432")
	user := getEnv("DB_USER", "cdn_app")
	password := getEnv("DB_PASSWORD", "changeme_cdn_password")
	dbname := getEnv("DB_NAME", "cdn")
	sslmode := getEnv("DB_SSLMODE", "disable")

	connStr := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
		host, port, user, password, dbname, sslmode,
	)

	db, err := sql.Open("postgres", connStr)
	if err != nil {
		log.Fatalf("Failed to open database connection: %v", err)
	}

	// Connection pool configuration for high throughput
	db.SetMaxOpenConns(100)               // Maximum open connections
	db.SetMaxIdleConns(50)                // Keep connections ready
	db.SetConnMaxLifetime(time.Hour)      // Recycle connections hourly
	db.SetConnMaxIdleTime(10 * time.Minute) // Close idle connections after 10 min

	// Test connection
	if err := db.Ping(); err != nil {
		log.Fatalf("Failed to ping database: %v", err)
	}

	log.Printf("Connected to PostgreSQL at %s:%s", host, port)

	return db
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
