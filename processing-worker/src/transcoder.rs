use std::path::{Path, PathBuf};
use tokio::process::Command;
use tokio::fs;
use crate::models::TranscodeProfile;

pub struct Transcoder {
    ffmpeg_path: String,
}

impl Transcoder {
    pub fn new() -> Self {
        Self {
            ffmpeg_path: "ffmpeg".to_string(),
        }
    }

    /// Transcode video to HLS with multiple bitrates
    pub async fn transcode_to_hls(
        &self,
        input_path: &Path,
        output_dir: &Path,
        profiles: &[TranscodeProfile],
    ) -> Result<(), anyhow::Error> {
        tracing::info!("Starting HLS transcoding for {:?}", input_path);

        // Create output directory
        fs::create_dir_all(output_dir).await?;

        // Transcode each profile in parallel
        let mut tasks = Vec::new();

        for profile in profiles {
            let input = input_path.to_path_buf();
            let output = output_dir.to_path_buf();
            let profile = profile.clone();
            let ffmpeg_path = self.ffmpeg_path.clone();

            let task = tokio::spawn(async move {
                Self::transcode_profile(&ffmpeg_path, &input, &output, &profile).await
            });

            tasks.push(task);
        }

        // Wait for all transcoding tasks to complete
        let results = futures::future::join_all(tasks).await;

        for result in results {
            result??;
        }

        // Generate master playlist
        self.generate_master_playlist(output_dir, profiles).await?;

        tracing::info!("HLS transcoding completed successfully");
        Ok(())
    }

    /// Transcode a single profile
    async fn transcode_profile(
        ffmpeg_path: &str,
        input_path: &Path,
        output_dir: &Path,
        profile: &TranscodeProfile,
    ) -> Result<(), anyhow::Error> {
        let output_file = output_dir.join(format!("{}.m3u8", profile.name));
        let segment_pattern = output_dir.join(format!("{}_%03d.ts", profile.name));

        tracing::info!("Transcoding {} profile", profile.name);

        let output = Command::new(ffmpeg_path)
            .args(&[
                "-i", input_path.to_str().unwrap(),
                "-vf", &format!("scale={}:{}", profile.width, profile.height),
                "-c:v", "libx264",
                "-b:v", &profile.bitrate,
                "-c:a", "aac",
                "-b:a", &profile.audio_bitrate,
                "-preset", "fast",
                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",
                "-f", "hls",
                "-hls_time", "4",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", segment_pattern.to_str().unwrap(),
                output_file.to_str().unwrap(),
            ])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .output()
            .await?;

        if !output.status.success() {
            tracing::error!("FFmpeg failed for {} profile", profile.name);
            anyhow::bail!("FFmpeg failed for {} profile", profile.name);
        }

        tracing::info!("Completed {} profile", profile.name);
        Ok(())
    }

    /// Generate master HLS playlist
    async fn generate_master_playlist(
        &self,
        output_dir: &Path,
        profiles: &[TranscodeProfile],
    ) -> Result<(), anyhow::Error> {
        let mut playlist = String::from("#EXTM3U\n#EXT-X-VERSION:3\n\n");

        for profile in profiles {
            let bandwidth = profile.bitrate
                .trim_end_matches('k')
                .parse::<u32>()
                .unwrap_or(5000)
                * 1000;

            playlist.push_str(&format!(
                "#EXT-X-STREAM-INF:BANDWIDTH={},RESOLUTION={}x{}\n",
                bandwidth, profile.width, profile.height
            ));
            playlist.push_str(&format!("{}.m3u8\n\n", profile.name));
        }

        let master_file = output_dir.join("master.m3u8");
        fs::write(&master_file, playlist).await?;

        tracing::info!("Generated master playlist at {:?}", master_file);
        Ok(())
    }

    /// Get video metadata using ffprobe
    pub async fn get_video_info(&self, input_path: &Path) -> Result<VideoInfo, anyhow::Error> {
        let output = Command::new("ffprobe")
            .args(&[
                "-v", "error",
                "-show_entries", "format=duration,size",
                "-show_entries", "stream=width,height,codec_name",
                "-of", "json",
                input_path.to_str().unwrap(),
            ])
            .output()
            .await?;

        if !output.status.success() {
            anyhow::bail!("ffprobe failed");
        }

        // For simplicity, return basic info
        // In production, parse the JSON output properly
        Ok(VideoInfo {
            duration: 0.0,
            width: 1920,
            height: 1080,
        })
    }
}

#[derive(Debug)]
pub struct VideoInfo {
    pub duration: f64,
    pub width: u32,
    pub height: u32,
}
