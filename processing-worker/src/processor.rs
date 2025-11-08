use std::path::PathBuf;
use tokio::fs;
use uuid::Uuid;
use sqlx::PgPool;

use crate::{
    config::Config,
    models::{ProcessingJob, TranscodeProfile},
    storage::StorageClient,
    transcoder::Transcoder,
    db,
};

pub struct VideoProcessor {
    config: Config,
    storage: StorageClient,
    transcoder: Transcoder,
    db_pool: PgPool,
}

impl VideoProcessor {
    pub fn new(
        config: Config,
        storage: StorageClient,
        db_pool: PgPool,
    ) -> Self {
        Self {
            config,
            storage,
            transcoder: Transcoder::new(),
            db_pool,
        }
    }

    /// Process a video job
    pub async fn process_job(&self, job: &ProcessingJob) -> Result<(), anyhow::Error> {
        let video_id = Uuid::parse_str(&job.video_id)?;

        tracing::info!("Processing job for video: {}", video_id);

        // Update job status to PROCESSING
        db::update_job_status(&self.db_pool, video_id, "PROCESSING").await?;
        db::update_video_status(&self.db_pool, video_id, "PROCESSING").await?;

        // Process the video
        match self.process_video(video_id).await {
            Ok(_) => {
                // Mark job as completed
                db::mark_job_completed(&self.db_pool, video_id).await?;
                tracing::info!("Job completed successfully for video: {}", video_id);
                Ok(())
            }
            Err(e) => {
                // Mark job as failed
                let error_msg = e.to_string();
                db::mark_job_failed(&self.db_pool, video_id, &error_msg).await?;
                tracing::error!("Job failed for video {}: {}", video_id, error_msg);
                Err(e)
            }
        }
    }

    /// Process video: download, transcode, upload
    async fn process_video(&self, video_id: Uuid) -> Result<(), anyhow::Error> {
        // Create temporary directories
        let work_dir = PathBuf::from(&self.config.temp_dir).join(video_id.to_string());
        let input_dir = work_dir.join("input");
        let output_dir = work_dir.join("output");

        fs::create_dir_all(&input_dir).await?;
        fs::create_dir_all(&output_dir).await?;

        // Download raw video from MinIO
        let raw_object_key = format!("raw/{}", video_id);

        // Find the actual file in the raw directory
        // For simplicity, assume the file exists with a known pattern
        // In production, you'd query the database for the actual filename
        let input_file = input_dir.join("input.mp4");

        tracing::info!("Downloading video from MinIO...");
        // Note: We need to know the exact object key including filename
        // This should be stored in the database
        let object_key = format!("raw/{}/video.mp4", video_id); // Simplified

        // For now, let's construct a more realistic path
        // We'll need to query the DB for the actual filename
        self.storage.download_video(&object_key, &input_file).await?;

        // Transcode video to HLS
        tracing::info!("Transcoding video to HLS...");
        let profiles = TranscodeProfile::profiles();
        self.transcoder.transcode_to_hls(&input_file, &output_dir, &profiles).await?;

        // Upload all HLS files to MinIO
        tracing::info!("Uploading HLS files to MinIO...");
        self.upload_hls_files(&output_dir, video_id).await?;

        // Clean up temporary files
        tracing::info!("Cleaning up temporary files...");
        fs::remove_dir_all(&work_dir).await?;

        Ok(())
    }

    /// Upload all HLS files (playlists and segments) to MinIO
    async fn upload_hls_files(
        &self,
        output_dir: &PathBuf,
        video_id: Uuid,
    ) -> Result<(), anyhow::Error> {
        let mut entries = fs::read_dir(output_dir).await?;

        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();

            if path.is_file() {
                let filename = path.file_name()
                    .and_then(|n| n.to_str())
                    .ok_or_else(|| anyhow::anyhow!("Invalid filename"))?;

                let object_key = format!("hls/{}/{}", video_id, filename);

                let content_type = if filename.ends_with(".m3u8") {
                    "application/vnd.apple.mpegurl"
                } else if filename.ends_with(".ts") {
                    "video/mp2t"
                } else {
                    "application/octet-stream"
                };

                self.storage.upload_video(&path, &object_key, content_type).await?;
            }
        }

        tracing::info!("All HLS files uploaded successfully");
        Ok(())
    }
}
