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

    tracing::info!("Starting processing worker...");

    // Load configuration
    let config = Config::from_env()?;
    tracing::info!("Worker ID: {}", config.worker_id);
    tracing::info!("Concurrency: {}", config.worker_concurrency);

    // Initialize database pool
    tracing::info!("Connecting to database...");
    let db_pool = db::create_pool(&config.database_url).await?;
    tracing::info!("Database connection established");

    // Initialize storage client
    tracing::info!("Initializing storage client...");
    let storage = StorageClient::new(&config).await?;
    tracing::info!("Storage client initialized");

    // Initialize job queue
    tracing::info!("Connecting to Redis job queue...");
    let mut queue = JobQueue::new(&config.redis_url, &config.worker_id).await?;
    queue.init_consumer_group().await?;
    tracing::info!("Job queue connected");

    // Create video processor
    let processor = VideoProcessor::new(config.clone(), storage, db_pool);

    tracing::info!("Worker ready! Waiting for jobs...");

    // Main processing loop
    loop {
        match process_jobs(&mut queue, &processor, config.worker_concurrency).await {
            Ok(_) => {}
            Err(e) => {
                tracing::error!("Error processing jobs: {}", e);
                // Sleep before retrying
                tokio::time::sleep(tokio::time::Duration::from_secs(5)).await;
            }
        }
    }
}

async fn process_jobs(
    queue: &mut JobQueue,
    processor: &VideoProcessor,
    _concurrency: usize,
) -> Result<(), anyhow::Error> {
    // Read jobs from the queue (block for 5 seconds if no jobs)
    let jobs = queue.read_jobs(1, 5000).await?;

    if jobs.is_empty() {
        return Ok(());
    }

    for (message_id, job) in jobs {
        tracing::info!("Received job: {:?}", job);

        // Process the job
        match processor.process_job(&job).await {
            Ok(_) => {
                // Acknowledge the job
                queue.ack_job(&message_id).await?;
                tracing::info!("Job acknowledged: {}", message_id);
            }
            Err(e) => {
                tracing::error!("Failed to process job {}: {}", message_id, e);
                // Don't acknowledge - let it be retried or handled by dead letter queue
            }
        }
    }

    Ok(())
}
