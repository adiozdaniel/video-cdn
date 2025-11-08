use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Json},
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::Arc;
use tokio::sync::Mutex;
use uuid::Uuid;

use crate::{models::*, services::UploadService};

pub type SharedUploadService = Arc<Mutex<UploadService>>;

#[derive(Debug)]
pub enum ApiError {
    Internal(String),
    NotFound(String),
    BadRequest(String),
}

impl IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (status, message) = match self {
            ApiError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            ApiError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            ApiError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
        };

        (status, Json(json!({"error": message}))).into_response()
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(err: anyhow::Error) -> Self {
        ApiError::Internal(err.to_string())
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(err: sqlx::Error) -> Self {
        ApiError::Internal(err.to_string())
    }
}

pub async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "healthy".to_string(),
        service: "upload-service".to_string(),
        time: chrono::Utc::now().timestamp(),
    })
}

pub async fn initiate_upload(
    State(service): State<SharedUploadService>,
    Json(req): Json<InitiateUploadRequest>,
) -> Result<Json<InitiateUploadResponse>, ApiError> {
    let service = service.lock().await;
    let response = service.initiate_upload(req.filename, req.size).await?;
    Ok(Json(response))
}

pub async fn complete_upload(
    State(service): State<SharedUploadService>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let video_id = Uuid::parse_str(&id)
        .map_err(|_| ApiError::BadRequest("Invalid video ID".to_string()))?;

    let mut service = service.lock().await;
    service.complete_upload(video_id).await?;
    Ok(StatusCode::OK)
}

pub async fn cancel_upload(
    State(service): State<SharedUploadService>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let video_id = Uuid::parse_str(&id)
        .map_err(|_| ApiError::BadRequest("Invalid video ID".to_string()))?;

    let service = service.lock().await;
    service.cancel_upload(video_id).await?;
    Ok(StatusCode::OK)
}

pub async fn get_status(
    State(service): State<SharedUploadService>,
    Path(id): Path<String>,
) -> Result<Json<UploadStatus>, ApiError> {
    let video_id = Uuid::parse_str(&id)
        .map_err(|_| ApiError::BadRequest("Invalid video ID".to_string()))?;

    let service = service.lock().await;
    let status = service.get_upload_status(video_id).await?;
    Ok(Json(status))
}

#[derive(Debug, Deserialize)]
pub struct ListVideosQuery {
    limit: Option<i64>,
    offset: Option<i64>,
}

#[derive(Debug, Serialize)]
pub struct ListVideosResponse {
    videos: Vec<Video>,
    limit: i64,
    offset: i64,
    count: usize,
}

pub async fn list_videos(
    State(service): State<SharedUploadService>,
    Query(params): Query<ListVideosQuery>,
) -> Result<Json<ListVideosResponse>, ApiError> {
    let service = service.lock().await;
    let videos = service.list_videos(params.limit, params.offset).await?;
    let count = videos.len();

    Ok(Json(ListVideosResponse {
        videos,
        limit: params.limit.unwrap_or(50),
        offset: params.offset.unwrap_or(0),
        count,
    }))
}

pub async fn get_video(
    State(service): State<SharedUploadService>,
    Path(id): Path<String>,
) -> Result<Json<Video>, ApiError> {
    let video_id = Uuid::parse_str(&id)
        .map_err(|_| ApiError::BadRequest("Invalid video ID".to_string()))?;

    let service = service.lock().await;
    let video = service.get_video(video_id).await?;
    Ok(Json(video))
}
