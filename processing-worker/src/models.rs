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
        ]
    }
}
