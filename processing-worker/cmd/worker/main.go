package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"sync"
	"syscall"

	"processing-worker/internal/processor"
	"processing-worker/pkg/database"
	"processing-worker/pkg/minio"
	"processing-worker/pkg/redis"
)

func main() {
	// Maximize CPU usage for video processing
	runtime.GOMAXPROCS(runtime.NumCPU())

	workerID := getEnv("WORKER_ID", "worker-unknown")
	concurrency := getEnvInt("WORKER_CONCURRENCY", 4)

	log.Printf("Starting worker %s with %d concurrent jobs (CPU cores: %d)",
		workerID, concurrency, runtime.NumCPU())

	// Initialize clients
	db := database.NewClient()
	defer db.Close()

	minioClient := minio.NewClient()
	redisClient := redis.NewClient()
	defer redisClient.Close()

	// Create processor pool
	var wg sync.WaitGroup
	ctx, cancel := context.WithCancel(context.Background())

	for i := 0; i < concurrency; i++ {
		wg.Add(1)
		go func(processorID int) {
			defer wg.Done()

			proc := processor.NewProcessor(
				processorID,
				workerID,
				db,
				minioClient,
				redisClient,
			)

			log.Printf("Started processor %d", processorID)
			proc.Start(ctx)
		}(i)
	}

	log.Printf("Worker %s started with %d processors", workerID, concurrency)

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down worker...")
	cancel()
	wg.Wait()

	log.Println("Worker stopped gracefully")
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if value := os.Getenv(key); value != "" {
		if intVal, err := strconv.Atoi(value); err == nil {
			return intVal
		}
	}
	return fallback
}
