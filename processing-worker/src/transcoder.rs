use std::path::{Path, PathBuf};
use tokio::process::Command;
use tokio::fs;
use crate::models::TranscodeProfile;
use serde::Deserialize;

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
        video_info: &VideoInfo,
    ) -> Result<(), anyhow::Error> {
        tracing::info!("Starting HLS transcoding for {:?}", input_path);

        // Create output directory
        fs::create_dir_all(output_dir).await?;

        // Select FFmpeg preset based on video duration
        // Longer videos get faster presets to reduce processing time
        let preset = if video_info.duration > 600.0 {
            // > 10 minutes: use ultrafast
            "ultrafast"
        } else if video_info.duration > 300.0 {
            // > 5 minutes: use veryfast
            "veryfast"
        } else if video_info.duration > 120.0 {
            // > 2 minutes: use faster
            "faster"
        } else {
            // <= 2 minutes: use fast (better quality)
            "fast"
        };

        tracing::info!("Using '{}' preset for {:.1}s video", preset, video_info.duration);

        // Transcode each profile in parallel
        let mut tasks = Vec::new();

        for profile in profiles {
            let input = input_path.to_path_buf();
            let output = output_dir.to_path_buf();
            let profile = profile.clone();
            let ffmpeg_path = self.ffmpeg_path.clone();
            let preset = preset.to_string();

            let task = tokio::spawn(async move {
                Self::transcode_profile(&ffmpeg_path, &input, &output, &profile, &preset, None, None, None).await
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

    /// Transcode a single profile (supports optional chunk time range)
    pub async fn transcode_single_profile(
        &self,
        input_path: &Path,
        output_dir: &Path,
        profile: &TranscodeProfile,
        preset: &str,
        chunk_id: Option<i32>,
        start_time: Option<f64>,
        end_time: Option<f64>,
    ) -> Result<(), anyhow::Error> {
        Self::transcode_profile(
            &self.ffmpeg_path,
            input_path,
            output_dir,
            profile,
            preset,
            chunk_id,
            start_time,
            end_time,
        ).await
    }

    /// Transcode a single profile (with optional chunk support)
    async fn transcode_profile(
        ffmpeg_path: &str,
        input_path: &Path,
        output_dir: &Path,
        profile: &TranscodeProfile,
        preset: &str,
        chunk_id: Option<i32>,
        start_time: Option<f64>,
        end_time: Option<f64>,
    ) -> Result<(), anyhow::Error> {
        // Adjust output filenames for chunks
        let (output_file, segment_pattern) = if let Some(chunk) = chunk_id {
            let output_file = output_dir.join(format!("{}_chunk{}.m3u8", profile.name, chunk));
            let segment_pattern = output_dir.join(format!("{}_chunk{}_%03d.ts", profile.name, chunk));
            (output_file, segment_pattern)
        } else {
            let output_file = output_dir.join(format!("{}.m3u8", profile.name));
            let segment_pattern = output_dir.join(format!("{}_%03d.ts", profile.name));
            (output_file, segment_pattern)
        };

        let chunk_info = if let Some(chunk) = chunk_id {
            format!(" (chunk {})", chunk)
        } else {
            String::new()
        };

        tracing::info!("Transcoding {} profile{} with {} preset", profile.name, chunk_info, preset);

        // Use CRF (Constant Rate Factor) instead of CBR for faster encoding
        // CRF values: 18-23 is good quality range (lower = better quality, slower)
        // We use higher CRF for lower resolutions to save processing time
        let crf = match profile.height {
            h if h >= 1080 => "23",  // 1080p: Best quality
            h if h >= 720 => "24",   // 720p: Good quality
            h if h >= 480 => "25",   // 480p: Medium quality
            _ => "26",               // 360p/240p: Lower quality is acceptable
        };

        // Build FFmpeg args with optional chunk time range
        let mut ffmpeg_args: Vec<String> = Vec::new();

        // Add chunk time range if specified (must come BEFORE -i)
        if let (Some(start), Some(end)) = (start_time, end_time) {
            let duration = end - start;
            ffmpeg_args.push("-ss".to_string());
            ffmpeg_args.push(start.to_string());
            ffmpeg_args.push("-t".to_string());
            ffmpeg_args.push(duration.to_string());
            tracing::info!("Processing chunk from {:.2}s to {:.2}s (duration: {:.2}s)", start, end, duration);
        }

        // Input file
        ffmpeg_args.push("-i".to_string());
        ffmpeg_args.push(input_path.to_str().unwrap().to_string());

        // Video encoding settings
        ffmpeg_args.push("-vf".to_string());
        ffmpeg_args.push(format!("scale={}:{}", profile.width, profile.height));
        ffmpeg_args.push("-c:v".to_string());
        ffmpeg_args.push("libx264".to_string());
        ffmpeg_args.push("-crf".to_string());
        ffmpeg_args.push(crf.to_string());
        ffmpeg_args.push("-maxrate".to_string());
        ffmpeg_args.push(profile.bitrate.clone());
        ffmpeg_args.push("-bufsize".to_string());
        ffmpeg_args.push(format!("{}k", profile.bitrate.trim_end_matches('k').parse::<u32>().unwrap_or(5000) * 2));

        // Audio encoding settings
        ffmpeg_args.push("-c:a".to_string());
        ffmpeg_args.push("aac".to_string());
        ffmpeg_args.push("-b:a".to_string());
        ffmpeg_args.push(profile.audio_bitrate.clone());

        // Encoding preset and GOP settings
        ffmpeg_args.push("-preset".to_string());
        ffmpeg_args.push(preset.to_string());
        ffmpeg_args.push("-g".to_string());
        ffmpeg_args.push("48".to_string());
        ffmpeg_args.push("-keyint_min".to_string());
        ffmpeg_args.push("48".to_string());
        ffmpeg_args.push("-sc_threshold".to_string());
        ffmpeg_args.push("0".to_string());

        // HLS settings
        ffmpeg_args.push("-f".to_string());
        ffmpeg_args.push("hls".to_string());
        ffmpeg_args.push("-hls_time".to_string());
        ffmpeg_args.push("4".to_string());
        ffmpeg_args.push("-hls_playlist_type".to_string());
        ffmpeg_args.push("vod".to_string());
        ffmpeg_args.push("-hls_segment_filename".to_string());
        ffmpeg_args.push(segment_pattern.to_str().unwrap().to_string());

        // Output file
        ffmpeg_args.push(output_file.to_str().unwrap().to_string());

        let output = Command::new(ffmpeg_path)
            .args(&ffmpeg_args)
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
    pub async fn generate_master_playlist_public(
        &self,
        output_dir: &Path,
        profiles: &[TranscodeProfile],
    ) -> Result<(), anyhow::Error> {
        self.generate_master_playlist(output_dir, profiles).await
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
        tracing::info!("Probing video metadata for {:?}", input_path);

        let output = Command::new("ffprobe")
            .args(&[
                "-v", "error",
                "-show_entries", "format=duration,size",
                "-show_entries", "stream=width,height,codec_name,codec_type",
                "-of", "json",
                input_path.to_str().unwrap(),
            ])
            .output()
            .await?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            anyhow::bail!("ffprobe failed: {}", stderr);
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let probe_data: FfprobeOutput = serde_json::from_str(&stdout)?;

        // Find the first video stream
        let video_stream = probe_data.streams.iter()
            .find(|s| s.codec_type.as_deref() == Some("video"))
            .ok_or_else(|| anyhow::anyhow!("No video stream found"))?;

        let width = video_stream.width.unwrap_or(1920);
        let height = video_stream.height.unwrap_or(1080);
        let duration = probe_data.format.duration
            .and_then(|d| d.parse::<f64>().ok())
            .unwrap_or(0.0);

        tracing::info!("Video info: {}x{}, {:.2}s duration", width, height, duration);

        Ok(VideoInfo {
            duration,
            width,
            height,
        })
    }
}

#[derive(Debug)]
pub struct VideoInfo {
    pub duration: f64,
    pub width: u32,
    pub height: u32,
}

// FFprobe JSON output structures
#[derive(Debug, Deserialize)]
struct FfprobeOutput {
    streams: Vec<FfprobeStream>,
    format: FfprobeFormat,
}

#[derive(Debug, Deserialize)]
struct FfprobeStream {
    codec_type: Option<String>,
    codec_name: Option<String>,
    width: Option<u32>,
    height: Option<u32>,
}

#[derive(Debug, Deserialize)]
struct FfprobeFormat {
    duration: Option<String>,
    size: Option<String>,
}
