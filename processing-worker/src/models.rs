use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Serialize, Deserialize)]
pub struct ProcessingJob {
    #[serde(rename = "videoId")]
    pub video_id: String,
    #[serde(rename = "type")]
    pub job_type: String,
    pub priority: String,
    pub timestamp: i64,
}

/// Profile-specific job message from orchestrator
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProfileJobMessage {
    #[serde(rename = "videoId")]
    pub video_id: String,
    pub profile: String,
    #[serde(rename = "inputPath")]
    pub input_path: String,
    #[serde(rename = "outputPath")]
    pub output_path: String,
    pub preset: String,
    pub priority: Option<String>,
    pub timestamp: Option<i64>,

    // Optional chunk fields (null = whole video)
    #[serde(rename = "chunkId")]
    pub chunk_id: Option<i32>,
    #[serde(rename = "startTime")]
    pub start_time: Option<f64>,
    #[serde(rename = "endTime")]
    pub end_time: Option<f64>,
}

/// Completion event to send back to orchestrator
#[derive(Debug, Serialize)]
pub struct CompletionEvent {
    #[serde(rename = "videoId")]
    pub video_id: String,
    pub profile: String,
    pub status: String,
    #[serde(rename = "workerId")]
    pub worker_id: String,

    // Metrics
    #[serde(rename = "durationMs")]
    pub duration_ms: Option<i64>,
    #[serde(rename = "filesUploaded")]
    pub files_uploaded: Option<i32>,

    // Optional chunk support
    #[serde(rename = "chunkId")]
    pub chunk_id: Option<i32>,

    // Error handling
    #[serde(rename = "errorMessage")]
    pub error_message: Option<String>,
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct Video {
    pub id: Uuid,
    pub filename: String,
    pub size: i64,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub uploaded_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone)]
pub struct TranscodeProfile {
    pub name: String,
    pub width: u32,
    pub height: u32,
    pub bitrate: String,
    pub audio_bitrate: String,
}

impl TranscodeProfile {
    pub fn profiles() -> Vec<Self> {
        vec![
            TranscodeProfile {
                name: "1080p".to_string(),
                width: 1920,
                height: 1080,
                bitrate: "5000k".to_string(),
                audio_bitrate: "192k".to_string(),
            },
            TranscodeProfile {
                name: "720p".to_string(),
                width: 1280,
                height: 720,
                bitrate: "3000k".to_string(),
                audio_bitrate: "128k".to_string(),
            },
            TranscodeProfile {
                name: "480p".to_string(),
                width: 854,
                height: 480,
                bitrate: "1500k".to_string(),
                audio_bitrate: "96k".to_string(),
            },
            TranscodeProfile {
                name: "360p".to_string(),
                width: 640,
                height: 360,
                bitrate: "800k".to_string(),
                audio_bitrate: "96k".to_string(),
            },
            TranscodeProfile {
                name: "240p".to_string(),
                width: 426,
                height: 240,
                bitrate: "400k".to_string(),
                audio_bitrate: "64k".to_string(),
            },
        ]
    }
}
