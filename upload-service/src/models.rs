use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Serialize, Deserialize)]
pub struct InitiateUploadRequest {
    pub filename: String,
    pub size: i64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct InitiateUploadResponse {
    #[serde(rename = "videoId")]
    pub video_id: String,
    #[serde(rename = "uploadUrl")]
    pub upload_url: String,
    #[serde(rename = "expiresIn")]
    pub expires_in: i64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct UploadStatus {
    #[serde(rename = "videoId")]
    pub video_id: String,
    pub status: String,
    pub filename: String,
    pub size: i64,
    #[serde(rename = "createdAt")]
    pub created_at: DateTime<Utc>,
    #[serde(rename = "uploadedAt", skip_serializing_if = "Option::is_none")]
    pub uploaded_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct Video {
    pub id: Uuid,
    pub filename: String,
    pub size: i64,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub uploaded_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ProcessingJob {
    #[serde(rename = "videoId")]
    pub video_id: String,
    #[serde(rename = "type")]
    pub job_type: String,
    pub priority: String,
    pub timestamp: i64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HealthResponse {
    pub status: String,
    pub service: String,
    pub time: i64,
}
