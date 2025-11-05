package redis

import (
	"context"
	"log"
	"os"
	"time"

	"github.com/redis/go-redis/v9"
)

// NewClient creates a new Redis client with optimized connection pooling
func NewClient() *redis.Client {
	addr := getEnv("REDIS_ADDR", "redis:6379")
	password := getEnv("REDIS_PASSWORD", "")
	db := 0 // Default DB

	client := redis.NewClient(&redis.Options{
		Addr:         addr,
		Password:     password,
		DB:           db,
		PoolSize:     100,               // High connection pool for throughput
		MinIdleConns: 20,                // Maintain idle connections
		MaxRetries:   3,                 // Retry failed commands
		DialTimeout:  5 * time.Second,   // Connection timeout
		ReadTimeout:  3 * time.Second,   // Read timeout
		WriteTimeout: 3 * time.Second,   // Write timeout
		PoolTimeout:  4 * time.Second,   // Pool wait timeout
	})

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		log.Fatalf("Failed to connect to Redis: %v", err)
	}

	log.Printf("Connected to Redis at %s", addr)

	// Create consumer group for processing jobs if it doesn't exist
	// This is for workers to consume jobs from Redis Streams
	err := client.XGroupCreateMkStream(ctx, "processing-jobs", "workers", "$").Err()
	if err != nil && err.Error() != "BUSYGROUP Consumer Group name already exists" {
		log.Printf("Warning: Failed to create consumer group: %v", err)
	}

	return client
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
