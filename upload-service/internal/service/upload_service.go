package service

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/minio/minio-go/v7"
	"github.com/redis/go-redis/v9"
)

type UploadService struct {
	db    *sql.DB
	minio *minio.Client
	redis *redis.Client
}

type InitiateUploadResponse struct {
	VideoID   string
	UploadURL string
	ExpiresIn int
}

type UploadStatus struct {
	VideoID    string    `json:"videoId"`
	Status     string    `json:"status"`
	Filename   string    `json:"filename"`
	Size       int64     `json:"size"`
	CreatedAt  time.Time `json:"createdAt"`
	UploadedAt *time.Time `json:"uploadedAt,omitempty"`
}

func NewUploadService(db *sql.DB, minioClient *minio.Client, redisClient *redis.Client) *UploadService {
	return &UploadService{
		db:    db,
		minio: minioClient,
		redis: redisClient,
	}
}

// InitiateUpload generates a presigned URL and creates video record
// Target: < 2ms latency for maximum throughput
func (s *UploadService) InitiateUpload(ctx context.Context, filename string, size int64) (*InitiateUploadResponse, error) {
	videoID := uuid.New().String()

	// Generate presigned PUT URL (1 hour expiry)
	// Client will upload directly to MinIO without going through our service
	objectPath := fmt.Sprintf("raw/%s/%s", videoID, filename)

	uploadURL, err := s.minio.PresignedPutObject(
		ctx,
		"videos",
		objectPath,
		time.Hour,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to generate presigned URL: %w", err)
	}

	// Insert video metadata into database (async preferred, but keeping sync for simplicity)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO videos (id, filename, size, status, created_at)
		 VALUES ($1, $2, $3, 'UPLOADING', NOW())`,
		videoID, filename, size,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create video record: %w", err)
	}

	return &InitiateUploadResponse{
		VideoID:   videoID,
		UploadURL: uploadURL.String(),
		ExpiresIn: 3600,
	}, nil
}

// CompleteUpload marks video as uploaded and queues processing job
func (s *UploadService) CompleteUpload(ctx context.Context, videoID string) error {
	// Update video status
	result, err := s.db.ExecContext(ctx,
		`UPDATE videos
		 SET status = 'UPLOADED', uploaded_at = NOW()
		 WHERE id = $1 AND status = 'UPLOADING'`,
		videoID,
	)
	if err != nil {
		return fmt.Errorf("failed to update video status: %w", err)
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to get rows affected: %w", err)
	}
	if rows == 0 {
		return fmt.Errorf("video not found or already processed")
	}

	// Create processing job record
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO processing_jobs (video_id, status, created_at)
		 VALUES ($1, 'QUEUED', NOW())`,
		videoID,
	)
	if err != nil {
		return fmt.Errorf("failed to create job record: %w", err)
	}

	// Push job to Redis Stream for workers to consume
	// Using Redis Streams for reliable job queue with consumer groups
	err = s.redis.XAdd(ctx, &redis.XAddArgs{
		Stream: "processing-jobs",
		MaxLen: 10000, // Prevent unbounded growth
		Approx: true,  // Allow approximate trimming for performance
		Values: map[string]interface{}{
			"videoId":   videoID,
			"type":      "transcode",
			"priority":  "normal",
			"timestamp": time.Now().Unix(),
		},
	}).Err()
	if err != nil {
		return fmt.Errorf("failed to queue job: %w", err)
	}

	return nil
}

// CancelUpload cancels an ongoing upload
func (s *UploadService) CancelUpload(ctx context.Context, videoID string) error {
	// Update video status to deleted
	result, err := s.db.ExecContext(ctx,
		`UPDATE videos
		 SET status = 'DELETED', deleted_at = NOW()
		 WHERE id = $1 AND status = 'UPLOADING'`,
		videoID,
	)
	if err != nil {
		return fmt.Errorf("failed to cancel upload: %w", err)
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to get rows affected: %w", err)
	}
	if rows == 0 {
		return fmt.Errorf("video not found or cannot be cancelled")
	}

	// Optionally: Delete from MinIO (async cleanup job preferred)
	// For now, we'll keep the data for potential recovery

	return nil
}

// GetUploadStatus returns the current status of an upload
func (s *UploadService) GetUploadStatus(ctx context.Context, videoID string) (*UploadStatus, error) {
	var status UploadStatus
	var uploadedAt sql.NullTime

	err := s.db.QueryRowContext(ctx,
		`SELECT id, filename, size, status, created_at, uploaded_at
		 FROM videos
		 WHERE id = $1`,
		videoID,
	).Scan(&status.VideoID, &status.Filename, &status.Size, &status.Status, &status.CreatedAt, &uploadedAt)

	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("video not found")
	}
	if err != nil {
		return nil, fmt.Errorf("failed to query video: %w", err)
	}

	if uploadedAt.Valid {
		status.UploadedAt = &uploadedAt.Time
	}

	return &status, nil
}
