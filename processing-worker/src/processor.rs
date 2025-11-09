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

        // Get the original filename from the database
        let filename = db::get_video_filename(&self.db_pool, video_id).await?;
        tracing::info!("Retrieved filename from database: {}", filename);

        // Download raw video from MinIO
        let object_key = format!("raw/{}/{}", video_id, filename);
        let input_file = input_dir.join("input.mp4");

        tracing::info!("Downloading video from MinIO...");
        self.storage.download_video(&object_key, &input_file).await?;

        // Probe video to get metadata
        let video_info = self.transcoder.get_video_info(&input_file).await?;

        tracing::info!(
            "Source video: {}x{} resolution, {:.1}s duration",
            video_info.width, video_info.height, video_info.duration
        );

        // Update progress to 10% (download complete, starting transcode)
        db::update_job_progress(&self.db_pool, video_id, 10).await.ok();

        // Select profiles adaptively - don't upscale
        let all_profiles = TranscodeProfile::profiles();
        let profiles: Vec<_> = all_profiles.into_iter()
            .filter(|p| p.height <= video_info.height)
            .collect();

        if profiles.is_empty() {
            anyhow::bail!("Source video resolution too low - minimum 240p required");
        }

        tracing::info!("Selected {} transcode profiles based on source resolution", profiles.len());

        // **PHASE 2: TIERED PROCESSING**
        // Generate 480p first for instant playback, then continue with other qualities

        // Separate 480p from other profiles
        let (profile_480p, other_profiles): (Vec<_>, Vec<_>) = profiles.into_iter()
            .partition(|p| p.height == 480);

        // STEP 1: Generate 480p first with ultrafast preset
        if !profile_480p.is_empty() {
            tracing::info!("🚀 PHASE 2: Generating 480p first for instant playback...");

            // Always use ultrafast for initial 480p
            self.transcoder.transcode_single_profile(
                &input_file,
                &output_dir,
                &profile_480p[0],
                "ultrafast"
            ).await?;

            // Update progress to 30% (480p complete)
            db::update_job_progress(&self.db_pool, video_id, 30).await.ok();

            // Upload 480p files to MinIO
            tracing::info!("📤 Uploading 480p files to MinIO...");
            self.upload_hls_files(&output_dir, video_id).await?;

            // Generate a basic master playlist with just 480p
            self.transcoder.generate_master_playlist_public(&output_dir, &profile_480p).await?;

            // Upload master playlist
            let master_playlist_path = output_dir.join("master.m3u8");
            let master_object_key = format!("hls/{}/master.m3u8", video_id);
            self.storage.upload_video(
                &master_playlist_path,
                &master_object_key,
                "application/vnd.apple.mpegurl"
            ).await?;

            // Mark video as PLAYABLE - user can start watching!
            tracing::info!("✅ Video is now PLAYABLE with 480p quality!");
            db::mark_video_playable(&self.db_pool, video_id).await?;

            // Update progress to 40% (480p uploaded, video playable)
            db::update_job_progress(&self.db_pool, video_id, 40).await.ok();
        }

        // STEP 2: Continue with remaining qualities in background
        if !other_profiles.is_empty() {
            tracing::info!("🔄 Continuing with {} additional quality profiles...", other_profiles.len());

            self.transcoder.transcode_to_hls(&input_file, &output_dir, &other_profiles, &video_info).await?;

            // Update progress to 80% (all transcoding complete, uploading remaining)
            db::update_job_progress(&self.db_pool, video_id, 80).await.ok();

            // Upload remaining HLS files to MinIO
            tracing::info!("📤 Uploading remaining quality files to MinIO...");
            self.upload_hls_files(&output_dir, video_id).await?;
        }

        // STEP 3: Generate final master playlist with all qualities
        tracing::info!("📝 Generating final master playlist with all qualities...");
        let all_generated_profiles: Vec<_> = profile_480p.into_iter()
            .chain(other_profiles.into_iter())
            .collect();

        self.transcoder.generate_master_playlist_public(&output_dir, &all_generated_profiles).await?;

        // Upload final master playlist
        let master_playlist_path = output_dir.join("master.m3u8");
        let master_object_key = format!("hls/{}/master.m3u8", video_id);
        self.storage.upload_video(
            &master_playlist_path,
            &master_object_key,
            "application/vnd.apple.mpegurl"
        ).await?;

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
