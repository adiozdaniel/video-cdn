use redis::{Client, aio::ConnectionManager, AsyncCommands};
use std::collections::HashMap;

#[derive(Clone)]
pub struct CacheClient {
    connection: ConnectionManager,
}

impl CacheClient {
    pub async fn new(redis_url: &str) -> Result<Self, anyhow::Error> {
        let client = Client::open(redis_url)?;
        let connection = ConnectionManager::new(client).await?;
        Ok(Self { connection })
    }

    pub async fn enqueue_processing_job(
        &mut self,
        video_id: &str,
        job_type: &str,
        priority: &str,
    ) -> Result<(), anyhow::Error> {
        let timestamp = chrono::Utc::now().timestamp();

        let job_data: HashMap<String, String> = [
            ("videoId".to_string(), video_id.to_string()),
            ("type".to_string(), job_type.to_string()),
            ("priority".to_string(), priority.to_string()),
            ("timestamp".to_string(), timestamp.to_string()),
        ]
        .iter()
        .cloned()
        .collect();

        let _: () = self
            .connection
            .xadd_maxlen(
                "processing-jobs",
                redis::streams::StreamMaxlen::Approx(10000),
                "*",
                &job_data.iter().map(|(k, v)| (k.as_str(), v.as_str())).collect::<Vec<_>>(),
            )
            .await?;

        tracing::info!("Enqueued processing job for video: {}", video_id);
        Ok(())
    }
}
