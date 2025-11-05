package minio

import (
	"log"
	"os"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

// NewClient creates a new MinIO client with connection pooling
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

	// Ensure bucket exists
	ensureBucket(client, "videos")

	return client
}

func ensureBucket(client *minio.Client, bucketName string) {
	exists, err := client.BucketExists(client.Context(), bucketName)
	if err != nil {
		log.Fatalf("Failed to check bucket existence: %v", err)
	}

	if !exists {
		err = client.MakeBucket(client.Context(), bucketName, minio.MakeBucketOptions{})
		if err != nil {
			log.Fatalf("Failed to create bucket: %v", err)
		}
		log.Printf("Created bucket: %s", bucketName)
	}
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
