use crate::{cache::CacheClient, models::*, storage::StorageClient};
use sqlx::{PgPool, query, query_as};
use uuid::Uuid;

#[derive(Clone)]
pub struct UploadService {
    db: PgPool,
    storage: StorageClient,
    cache: CacheClient,
}

impl UploadService {
    pub fn new(db: PgPool, storage: StorageClient, cache: CacheClient) -> Self {
        Self { db, storage, cache }
    }

    /// Initiate upload - generates presigned URL and creates video record
    /// Target: < 2ms latency for maximum throughput
    pub async fn initiate_upload(
        &self,
        filename: String,
        size: i64,
    ) -> Result<InitiateUploadResponse, anyhow::Error> {
        let video_id = Uuid::new_v4();

        // Generate presigned PUT URL (1 hour expiry)
        let object_path = format!("raw/{}/{}", video_id, filename);
        let upload_url = self.storage.generate_presigned_upload_url(&object_path, 3600).await?;

        // Insert video metadata into database
        query(
            "INSERT INTO videos (id, filename, size, status, created_at)
             VALUES ($1, $2, $3, 'UPLOADING', NOW())"
        )
        .bind(video_id)
        .bind(&filename)
        .bind(size)
        .execute(&self.db)
        .await?;

        tracing::info!("Initiated upload for video: {}", video_id);

        Ok(InitiateUploadResponse {
            video_id: video_id.to_string(),
            upload_url,
            expires_in: 3600,
        })
    }

    /// Complete upload - marks video as uploaded and queues processing job
    pub async fn complete_upload(&mut self, video_id: Uuid) -> Result<(), anyhow::Error> {
        // Update video status
        let result = query(
            "UPDATE videos
             SET status = 'UPLOADED', uploaded_at = NOW()
             WHERE id = $1 AND status = 'UPLOADING'"
        )
        .bind(video_id)
        .execute(&self.db)
        .await?;

        if result.rows_affected() == 0 {
            anyhow::bail!("Video not found or already processed");
        }

        // Create processing job record
        query(
            "INSERT INTO processing_jobs (video_id, status, created_at)
             VALUES ($1, 'QUEUED', NOW())"
        )
        .bind(video_id)
        .execute(&self.db)
        .await?;

        // Push job to Redis Stream for workers to consume
        self.cache
            .enqueue_processing_job(&video_id.to_string(), "transcode", "normal")
            .await?;

        tracing::info!("Completed upload for video: {}", video_id);
        Ok(())
    }

    /// Cancel upload - marks video as deleted
    pub async fn cancel_upload(&self, video_id: Uuid) -> Result<(), anyhow::Error> {
        let result = query(
            "UPDATE videos
             SET status = 'DELETED', deleted_at = NOW()
             WHERE id = $1 AND status = 'UPLOADING'"
        )
        .bind(video_id)
        .execute(&self.db)
        .await?;

        if result.rows_affected() == 0 {
            anyhow::bail!("Video not found or cannot be cancelled");
        }

        tracing::info!("Cancelled upload for video: {}", video_id);
        Ok(())
    }

    /// Get upload status
    pub async fn get_upload_status(&self, video_id: Uuid) -> Result<UploadStatus, anyhow::Error> {
        let video = query_as::<_, Video>(
            "SELECT id, filename, size, status, created_at, uploaded_at
             FROM videos
             WHERE id = $1"
        )
        .bind(video_id)
        .fetch_optional(&self.db)
        .await?
        .ok_or_else(|| anyhow::anyhow!("Video not found"))?;

        Ok(UploadStatus {
            video_id: video.id.to_string(),
            status: video.status,
            filename: video.filename,
            size: video.size,
            created_at: video.created_at,
            uploaded_at: video.uploaded_at,
        })
    }

    /// List all videos (excluding deleted)
    pub async fn list_videos(&self, limit: Option<i64>, offset: Option<i64>) -> Result<Vec<Video>, anyhow::Error> {
        let limit = limit.unwrap_or(50).min(100); // Max 100 per page
        let offset = offset.unwrap_or(0);

        let videos = query_as::<_, Video>(
            "SELECT id, filename, size, status, created_at, uploaded_at
             FROM videos
             WHERE deleted_at IS NULL
             ORDER BY created_at DESC
             LIMIT $1 OFFSET $2"
        )
        .bind(limit)
        .bind(offset)
        .fetch_all(&self.db)
        .await?;

        Ok(videos)
    }

    /// Get a specific video by ID
    pub async fn get_video(&self, video_id: Uuid) -> Result<Video, anyhow::Error> {
        let video = query_as::<_, Video>(
            "SELECT id, filename, size, status, created_at, uploaded_at
             FROM videos
             WHERE id = $1 AND deleted_at IS NULL"
        )
        .bind(video_id)
        .fetch_optional(&self.db)
        .await?
        .ok_or_else(|| anyhow::anyhow!("Video not found"))?;

        Ok(video)
    }
}
