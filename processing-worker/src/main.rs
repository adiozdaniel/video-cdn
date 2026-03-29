mod config;
mod db;
mod models;
mod processor;
mod queue;
mod storage;
mod transcoder;

use config::Config;
use processor::VideoProcessor;
use queue::JobQueue;
use storage::StorageClient;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> Result<(), anyhow::Error> {
    // Initialize tracing/logging
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "processing_worker=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    tracing::info!("🚀 Starting processing worker...");

    // Load configuration
    let config = Config::from_env()?;
    tracing::info!("Worker ID: {}", config.worker_id);
    tracing::info!("Profile: {}", config.profile);
    tracing::info!("Concurrency: {}", config.worker_concurrency);

    // Initialize storage client
    tracing::info!("Initializing storage client...");
    let storage = StorageClient::new(&config).await?;
    tracing::info!("Storage client initialized");

    // Initialize Kafka job queue
    tracing::info!("Connecting to Kafka...");
    let queue = JobQueue::new(&config.kafka_brokers, &config.kafka_group_id, &config.profile).await?;
    tracing::info!("Kafka consumer ready for profile: {}", config.profile);

    // Create video processor (single profile processing)
    let processor = VideoProcessor::new(config.clone(), storage);

    tracing::info!("✅ Worker ready! Waiting for {} profile jobs from Kafka", config.profile);

    // Main processing loop
    loop {
        match process_profile_jobs(&queue, &processor).await {
            Ok(_) => {}
            Err(e) => {
                tracing::error!("Error processing jobs: {}", e);
                // Sleep before retrying
                tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
            }
        }
    }
}

async fn process_profile_jobs(
    queue: &JobQueue,
    processor: &VideoProcessor,
) -> Result<(), anyhow::Error> {
    // Read profile jobs from Kafka (blocks for up to 5 seconds)
    let jobs = queue.read_profile_jobs().await?;

    if jobs.is_empty() {
        return Ok(());
    }

    for job in jobs {
        tracing::info!("📥 Received {} profile job for video {}", job.profile, job.video_id);

        if let Some(chunk_id) = job.chunk_id {
            tracing::info!("   └─ Chunk {} ({:.2}s - {:.2}s)",
                chunk_id,
                job.start_time.unwrap_or(0.0),
                job.end_time.unwrap_or(0.0)
            );
        }

        // Process the job and get completion event
        match processor.process_profile_job(&job).await {
            Ok(completion_event) => {
                // Publish completion event to orchestrator
                if let Err(e) = queue.publish_completion(&completion_event).await {
                    tracing::error!("Failed to publish completion event: {}", e);
                }
                // Kafka auto-commits offsets
            }
            Err(e) => {
                tracing::error!("Failed to process job: {}", e);
                // Kafka will retry based on consumer group settings
            }
        }
    }

    Ok(())
}
