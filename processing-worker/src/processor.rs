use std::path::PathBuf;
use std::time::Instant;
use tokio::fs;
use uuid::Uuid;

use crate::{
    config::Config,
    models::{ProfileJobMessage, CompletionEvent, TranscodeProfile},
    storage::StorageClient,
    transcoder::Transcoder,
};

pub struct VideoProcessor {
    config: Config,
    storage: StorageClient,
    transcoder: Transcoder,
}

impl VideoProcessor {
    pub fn new(
        config: Config,
        storage: StorageClient,
    ) -> Self {
        Self {
            config,
            storage,
            transcoder: Transcoder::new(),
        }
    }

    /// Process a single profile job (with optional chunk support)
    pub async fn process_profile_job(
        &self,
        job: &ProfileJobMessage,
    ) -> Result<CompletionEvent, anyhow::Error> {
        let video_id = Uuid::parse_str(&job.video_id)?;
        let start_time = Instant::now();

        let chunk_info = if let Some(chunk) = job.chunk_id {
            format!(" chunk {}", chunk)
        } else {
            String::new()
        };

        tracing::info!(
            "Processing {} profile{} for video {}",
            job.profile,
            chunk_info,
            video_id
        );

        match self.process_profile(video_id, job).await {
            Ok(files_uploaded) => {
                let duration_ms = start_time.elapsed().as_millis() as i64;

                tracing::info!(
                    "✅ Completed {} profile{} for {} in {:.2}s",
                    job.profile,
                    chunk_info,
                    video_id,
                    duration_ms as f64 / 1000.0
                );

                Ok(CompletionEvent {
                    video_id: job.video_id.clone(),
                    profile: job.profile.clone(),
                    status: "COMPLETED".to_string(),
                    worker_id: self.config.worker_id.clone(),
                    duration_ms: Some(duration_ms),
                    files_uploaded: Some(files_uploaded),
                    chunk_id: job.chunk_id,
                    error_message: None,
                })
            }
            Err(e) => {
                let duration_ms = start_time.elapsed().as_millis() as i64;
                let error_msg = e.to_string();

                tracing::error!(
                    "❌ Failed {} profile{} for {}: {}",
                    job.profile,
                    chunk_info,
                    video_id,
                    error_msg
                );

                Ok(CompletionEvent {
                    video_id: job.video_id.clone(),
                    profile: job.profile.clone(),
                    status: "FAILED".to_string(),
                    worker_id: self.config.worker_id.clone(),
                    duration_ms: Some(duration_ms),
                    files_uploaded: None,
                    chunk_id: job.chunk_id,
                    error_message: Some(error_msg),
                })
            }
        }
    }

    /// Process a single profile (download, transcode, upload)
    async fn process_profile(
        &self,
        video_id: Uuid,
        job: &ProfileJobMessage,
    ) -> Result<i32, anyhow::Error> {
        // Create temporary directories
        let work_dir = PathBuf::from(&self.config.temp_dir).join(format!(
            "{}-{}-{}",
            video_id,
            job.profile,
            job.chunk_id.unwrap_or(0)
        ));
        let input_dir = work_dir.join("input");
        let output_dir = work_dir.join("output");

        fs::create_dir_all(&input_dir).await?;
        fs::create_dir_all(&output_dir).await?;

        // Download raw video from MinIO
        let object_key = &job.input_path;
        let input_file = input_dir.join("input.mp4");

        tracing::info!("Downloading video from MinIO: {}", object_key);
        self.storage.download_video(object_key, &input_file).await?;

        // Find the matching transcode profile
        let profile = TranscodeProfile::profiles()
            .into_iter()
            .find(|p| p.name == job.profile)
            .ok_or_else(|| anyhow::anyhow!("Unknown profile: {}", job.profile))?;

        // Transcode with optional chunk time range
        tracing::info!(
            "Transcoding {} profile with {} preset{}",
            job.profile,
            job.preset,
            if job.chunk_id.is_some() {
                format!(" (chunk {})", job.chunk_id.unwrap())
            } else {
                String::new()
            }
        );

        self.transcoder
            .transcode_single_profile(
                &input_file,
                &output_dir,
                &profile,
                &job.preset,
                job.chunk_id,
                job.start_time,
                job.end_time,
            )
            .await?;

        // Upload HLS files to MinIO
        let files_uploaded = self.upload_hls_files(&output_dir, video_id, job).await?;

        // Clean up temporary files
        tracing::info!("Cleaning up temporary files...");
        fs::remove_dir_all(&work_dir).await?;

        Ok(files_uploaded)
    }

    /// Upload all HLS files (playlists and segments) to MinIO
    async fn upload_hls_files(
        &self,
        output_dir: &PathBuf,
        video_id: Uuid,
        job: &ProfileJobMessage,
    ) -> Result<i32, anyhow::Error> {
        let mut entries = fs::read_dir(output_dir).await?;
        let mut file_count = 0;

        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();

            if path.is_file() {
                let filename = path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .ok_or_else(|| anyhow::anyhow!("Invalid filename"))?;

                // Use output_path from job message
                let object_key = format!("{}{}", job.output_path, filename);

                let content_type = if filename.ends_with(".m3u8") {
                    "application/vnd.apple.mpegurl"
                } else if filename.ends_with(".ts") {
                    "video/mp2t"
                } else {
                    "application/octet-stream"
                };

                self.storage
                    .upload_video(&path, &object_key, content_type)
                    .await?;

                file_count += 1;
            }
        }

        tracing::info!(
            "Uploaded {} files to MinIO for {} profile{}",
            file_count,
            job.profile,
            if let Some(chunk) = job.chunk_id {
                format!(" chunk {}", chunk)
            } else {
                String::new()
            }
        );

        Ok(file_count)
    }
}
