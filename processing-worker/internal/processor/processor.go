package processor

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/redis/go-redis/v9"

	"processing-worker/internal/ffmpeg"
)

type Processor struct {
	id      int
	workerID string
	db      *sql.DB
	minio   *minio.Client
	redis   *redis.Client
}

type Bitrate struct {
	Name         string
	Resolution   string
	VideoBitrate string
	AudioBitrate string
	Scale        string
}

// Standard bitrate ladder for adaptive streaming
var bitrates = []Bitrate{
	{
		Name:         "1080p",
		Resolution:   "1920x1080",
		VideoBitrate: "5000k",
		AudioBitrate: "192k",
		Scale:        "1920:1080",
	},
	{
		Name:         "720p",
		Resolution:   "1280x720",
		VideoBitrate: "3000k",
		AudioBitrate: "128k",
		Scale:        "1280:720",
	},
	{
		Name:         "480p",
		Resolution:   "854x480",
		VideoBitrate: "1500k",
		AudioBitrate: "128k",
		Scale:        "854:480",
	},
}

func NewProcessor(id int, workerID string, db *sql.DB, minioClient *minio.Client, redisClient *redis.Client) *Processor {
	return &Processor{
		id:      id,
		workerID: workerID,
		db:      db,
		minio:   minioClient,
		redis:   redisClient,
	}
}

// Start begins processing jobs from Redis queue
func (p *Processor) Start(ctx context.Context) {
	consumerName := fmt.Sprintf("%s-proc-%d", p.workerID, p.id)

	for {
		select {
		case <-ctx.Done():
			log.Printf("Processor %d shutting down", p.id)
			return
		default:
			// Read from Redis Stream with blocking
			streams, err := p.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
				Group:    "workers",
				Consumer: consumerName,
				Streams:  []string{"processing-jobs", ">"},
				Count:    1,
				Block:    5 * time.Second,
			}).Result()

			if err != nil {
				if err != redis.Nil {
					log.Printf("Processor %d: Error reading from queue: %v", p.id, err)
				}
				continue
			}

			// Process each message
			for _, stream := range streams {
				for _, message := range stream.Messages {
					videoID, ok := message.Values["videoId"].(string)
					if !ok {
						log.Printf("Processor %d: Invalid message format", p.id)
						p.redis.XAck(ctx, "processing-jobs", "workers", message.ID)
						continue
					}

					log.Printf("Processor %d: Processing video %s", p.id, videoID)

					// Process the video
					if err := p.processVideo(ctx, videoID); err != nil {
						log.Printf("Processor %d: Error processing video %s: %v", p.id, videoID, err)
						p.updateJobStatus(ctx, videoID, "FAILED", err.Error())
					} else {
						log.Printf("Processor %d: Successfully processed video %s", p.id, videoID)
						p.updateJobStatus(ctx, videoID, "COMPLETED", "Processing completed successfully")
					}

					// Acknowledge message
					p.redis.XAck(ctx, "processing-jobs", "workers", message.ID)
				}
			}
		}
	}
}

// processVideo handles the complete video processing pipeline
func (p *Processor) processVideo(ctx context.Context, videoID string) error {
	// Update job status to PROCESSING
	p.updateJobStatus(ctx, videoID, "PROCESSING", "Starting video processing")

	// Get video metadata
	var filename string
	err := p.db.QueryRowContext(ctx,
		"SELECT filename FROM videos WHERE id = $1",
		videoID,
	).Scan(&filename)
	if err != nil {
		return fmt.Errorf("failed to get video metadata: %w", err)
	}

	// Create temporary directory for processing
	tmpDir := filepath.Join("/tmp", videoID)
	if err := os.MkdirAll(tmpDir, 0755); err != nil {
		return fmt.Errorf("failed to create temp directory: %w", err)
	}
	defer os.RemoveAll(tmpDir)

	// Download video from MinIO
	inputPath := filepath.Join(tmpDir, "input.mp4")
	objectPath := fmt.Sprintf("raw/%s/%s", videoID, filename)

	log.Printf("Processor %d: Downloading video from MinIO: %s", p.id, objectPath)
	err = p.minio.FGetObject(ctx, "videos", objectPath, inputPath, minio.GetObjectOptions{})
	if err != nil {
		return fmt.Errorf("failed to download video: %w", err)
	}

	// Transcode to multiple bitrates IN PARALLEL
	log.Printf("Processor %d: Starting parallel transcoding", p.id)
	if err := p.transcodeParallel(ctx, videoID, inputPath, tmpDir); err != nil {
		return fmt.Errorf("failed to transcode: %w", err)
	}

	// Generate and upload master playlist
	log.Printf("Processor %d: Generating master playlist", p.id)
	if err := p.createMasterPlaylist(ctx, videoID); err != nil {
		return fmt.Errorf("failed to create master playlist: %w", err)
	}

	// Update video status to READY
	_, err = p.db.ExecContext(ctx,
		"UPDATE videos SET status = 'READY', processed_at = NOW() WHERE id = $1",
		videoID,
	)
	if err != nil {
		return fmt.Errorf("failed to update video status: %w", err)
	}

	return nil
}

// transcodeParallel transcodes video to multiple bitrates simultaneously
func (p *Processor) transcodeParallel(ctx context.Context, videoID, inputPath, tmpDir string) error {
	var wg sync.WaitGroup
	errChan := make(chan error, len(bitrates))

	// Process each bitrate in parallel
	for _, br := range bitrates {
		wg.Add(1)
		go func(bitrate Bitrate) {
			defer wg.Done()

			log.Printf("Processor %d: Transcoding %s", p.id, bitrate.Name)

			outputDir := filepath.Join(tmpDir, bitrate.Name)
			if err := os.MkdirAll(outputDir, 0755); err != nil {
				errChan <- fmt.Errorf("failed to create output dir for %s: %w", bitrate.Name, err)
				return
			}

			// Transcode using FFmpeg
			if err := ffmpeg.TranscodeToHLS(inputPath, outputDir, bitrate); err != nil {
				errChan <- fmt.Errorf("failed to transcode %s: %w", bitrate.Name, err)
				return
			}

			// Upload segments to MinIO
			if err := p.uploadSegments(ctx, videoID, bitrate.Name, outputDir); err != nil {
				errChan <- fmt.Errorf("failed to upload %s segments: %w", bitrate.Name, err)
				return
			}

			log.Printf("Processor %d: Completed %s", p.id, bitrate.Name)
		}(br)
	}

	wg.Wait()
	close(errChan)

	// Check for errors
	if err := <-errChan; err != nil {
		return err
	}

	return nil
}

// uploadSegments uploads all segments for a bitrate to MinIO
func (p *Processor) uploadSegments(ctx context.Context, videoID, bitrateName, dir string) error {
	files, err := filepath.Glob(filepath.Join(dir, "*"))
	if err != nil {
		return fmt.Errorf("failed to list files: %w", err)
	}

	for _, file := range files {
		filename := filepath.Base(file)
		objectPath := fmt.Sprintf("processed/%s/%s/%s", videoID, bitrateName, filename)

		_, err := p.minio.FPutObject(ctx, "videos", objectPath, file, minio.PutObjectOptions{
			ContentType: getContentType(filename),
		})
		if err != nil {
			return fmt.Errorf("failed to upload %s: %w", filename, err)
		}
	}

	return nil
}

// createMasterPlaylist generates and uploads the master HLS playlist
func (p *Processor) createMasterPlaylist(ctx context.Context, videoID string) error {
	playlist := `#EXTM3U
#EXT-X-VERSION:3

#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
1080p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
720p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=854x480
480p/playlist.m3u8
`

	tmpFile := filepath.Join("/tmp", fmt.Sprintf("%s-master.m3u8", videoID))
	if err := os.WriteFile(tmpFile, []byte(playlist), 0644); err != nil {
		return fmt.Errorf("failed to write master playlist: %w", err)
	}
	defer os.Remove(tmpFile)

	objectPath := fmt.Sprintf("processed/%s/master.m3u8", videoID)
	_, err := p.minio.FPutObject(ctx, "videos", objectPath, tmpFile, minio.PutObjectOptions{
		ContentType: "application/vnd.apple.mpegurl",
	})
	if err != nil {
		return fmt.Errorf("failed to upload master playlist: %w", err)
	}

	return nil
}

// updateJobStatus updates the processing job status in the database
func (p *Processor) updateJobStatus(ctx context.Context, videoID, status, message string) {
	query := `
		UPDATE processing_jobs
		SET status = $1, message = $2, completed_at = CASE WHEN $1 IN ('COMPLETED', 'FAILED') THEN NOW() ELSE NULL END
		WHERE video_id = $3 AND status != 'COMPLETED'
	`
	_, err := p.db.ExecContext(ctx, query, status, message, videoID)
	if err != nil {
		log.Printf("Processor %d: Failed to update job status: %v", p.id, err)
	}
}

func getContentType(filename string) string {
	ext := filepath.Ext(filename)
	switch ext {
	case ".m3u8":
		return "application/vnd.apple.mpegurl"
	case ".ts":
		return "video/mp2t"
	case ".m4s":
		return "video/iso.segment"
	default:
		return "application/octet-stream"
	}
}
