package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"upload-service/internal/handler"
	"upload-service/internal/service"
	"upload-service/pkg/database"
	"upload-service/pkg/minio"
	"upload-service/pkg/redis"
)

func main() {
	// Maximize CPU utilization for throughput
	runtime.GOMAXPROCS(runtime.NumCPU())
	log.Printf("Starting upload service with %d CPU cores", runtime.NumCPU())

	// Initialize infrastructure clients
	db := database.NewClient()
	defer db.Close()

	minioClient := minio.NewClient()
	redisClient := redis.NewClient()
	defer redisClient.Close()

	// Initialize service layer
	uploadService := service.NewUploadService(db, minioClient, redisClient)

	// Initialize handlers
	uploadHandler := handler.NewUploadHandler(uploadService)

	// Setup Gin with production settings
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(gin.Recovery())

	// Custom logger middleware (lightweight)
	router.Use(func(c *gin.Context) {
		start := time.Now()
		c.Next()
		duration := time.Since(start)
		log.Printf("[%s] %s - %d - %v", c.Request.Method, c.Request.URL.Path, c.Writer.Status(), duration)
	})

	// Routes
	api := router.Group("/api/upload")
	{
		api.POST("/initiate", uploadHandler.Initiate)
		api.POST("/:id/complete", uploadHandler.Complete)
		api.POST("/:id/cancel", uploadHandler.Cancel)
		api.GET("/:id/status", uploadHandler.Status)
	}

	// Health check endpoint
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status":  "healthy",
			"service": "upload-service",
			"time":    time.Now().Unix(),
		})
	})

	// High-performance HTTP server configuration
	server := &http.Server{
		Addr:              ":8080",
		Handler:           router,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       120 * time.Second,
		MaxHeaderBytes:    1 << 20, // 1MB
		ReadHeaderTimeout: 5 * time.Second,
	}

	// Graceful shutdown
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	log.Println("Upload service started on :8080")

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}

	log.Println("Server exited")
}
