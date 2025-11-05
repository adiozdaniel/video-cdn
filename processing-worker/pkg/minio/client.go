package minio

import (
	"log"
	"os"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

func NewClient() *minio.Client {
	endpoint := getEnv("MINIO_ENDPOINT", "minio:9000")
	accessKey := getEnv("MINIO_ACCESS_KEY", "minioadmin")
	secretKey := getEnv("MINIO_SECRET_KEY", "minioadmin")
	useSSL := getEnv("MINIO_USE_SSL", "false") == "true"

	client, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: useSSL,
	})
	if err != nil {
		log.Fatalf("Failed to create MinIO client: %v", err)
	}

	log.Printf("Connected to MinIO at %s", endpoint)
	return client
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
