mod cache;
mod config;
mod db;
mod handlers;
mod models;
mod services;
mod storage;

use axum::{
    routing::{get, post},
    Router,
};
use std::sync::Arc;
use tokio::sync::Mutex;
use tower_http::cors::{Any, CorsLayer};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use crate::{
    cache::CacheClient,
    config::Config,
    db::create_pool,
    handlers::{cancel_upload, complete_upload, get_status, get_video, health, initiate_upload, list_videos},
    services::UploadService,
    storage::StorageClient,
};

#[tokio::main]
async fn main() -> Result<(), anyhow::Error> {
    // Initialize tracing/logging
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "upload_service=info,tower_http=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    tracing::info!("Starting upload service...");

    // Load configuration
    let config = Config::from_env()?;
    tracing::info!("Configuration loaded");

    // Initialize database pool
    tracing::info!("Connecting to database...");
    let db_pool = create_pool(&config.database_url).await?;
    tracing::info!("Database connection established");

    // Initialize storage client
    tracing::info!("Initializing storage client...");
    let storage = StorageClient::new(&config).await?;
    tracing::info!("Storage client initialized");

    // Initialize cache client
    tracing::info!("Connecting to Redis...");
    let cache = CacheClient::new(&config.redis_url).await?;
    tracing::info!("Redis connection established");

    // Initialize service
    let upload_service = UploadService::new(db_pool, storage, cache);
    let shared_service = Arc::new(Mutex::new(upload_service));

    // Configure CORS
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any)
        .max_age(std::time::Duration::from_secs(86400));

    // Build router
    let app = Router::new()
        .route("/health", get(health))
        .route("/api/upload/initiate", post(initiate_upload))
        .route("/api/upload/:id/complete", post(complete_upload))
        .route("/api/upload/:id/cancel", post(cancel_upload))
        .route("/api/upload/:id/status", get(get_status))
        .route("/api/videos", get(list_videos))
        .route("/api/videos/:id", get(get_video))
        .layer(cors)
        .with_state(shared_service);

    // Start server
    let addr = format!("0.0.0.0:{}", config.server_port);
    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!("Upload service listening on {}", addr);

    axum::serve(listener, app).await?;

    Ok(())
}
