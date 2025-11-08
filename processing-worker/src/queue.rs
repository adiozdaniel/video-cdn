use redis::{Client, aio::ConnectionManager, AsyncCommands, streams::StreamReadOptions, streams::StreamReadReply};
use crate::models::ProcessingJob;

pub struct JobQueue {
    connection: ConnectionManager,
    worker_id: String,
    consumer_group: String,
}

impl JobQueue {
    pub async fn new(redis_url: &str, worker_id: &str) -> Result<Self, anyhow::Error> {
        let client = Client::open(redis_url)?;
        let connection = ConnectionManager::new(client).await?;

        let consumer_group = "processing-workers".to_string();

        Ok(Self {
            connection,
            worker_id: worker_id.to_string(),
            consumer_group,
        })
    }

    /// Initialize consumer group (idempotent)
    pub async fn init_consumer_group(&mut self) -> Result<(), anyhow::Error> {
        let result: Result<String, redis::RedisError> = self
            .connection
            .xgroup_create_mkstream(
                "processing-jobs",
                &self.consumer_group,
                "$",
            )
            .await;

        match result {
            Ok(_) => {
                tracing::info!("Created consumer group: {}", self.consumer_group);
            }
            Err(e) => {
                if e.to_string().contains("BUSYGROUP") {
                    tracing::info!("Consumer group already exists: {}", self.consumer_group);
                } else {
                    return Err(e.into());
                }
            }
        }

        Ok(())
    }

    /// Read jobs from the stream
    pub async fn read_jobs(&mut self, count: usize, block: usize) -> Result<Vec<(String, ProcessingJob)>, anyhow::Error> {
        let opts = StreamReadOptions::default()
            .count(count)
            .block(block)
            .group(&self.consumer_group, &self.worker_id);

        let result: StreamReadReply = self
            .connection
            .xread_options(&["processing-jobs"], &[">"], &opts)
            .await?;

        let mut jobs = Vec::new();

        for stream_key in result.keys {
            for stream_id in stream_key.ids {
                let id = stream_id.id.clone();

                // Helper function to extract string from redis::Value
                fn get_string(value: &redis::Value) -> Option<String> {
                    match value {
                        redis::Value::BulkString(bytes) => String::from_utf8(bytes.clone()).ok(),
                        redis::Value::SimpleString(s) => Some(s.clone()),
                        _ => None,
                    }
                }

                let video_id = stream_id.map.get("videoId")
                    .and_then(get_string)
                    .ok_or_else(|| anyhow::anyhow!("Missing videoId"))?;

                let job_type = stream_id.map.get("type")
                    .and_then(get_string)
                    .unwrap_or_else(|| "transcode".to_string());

                let priority = stream_id.map.get("priority")
                    .and_then(get_string)
                    .unwrap_or_else(|| "normal".to_string());

                let timestamp = stream_id.map.get("timestamp")
                    .and_then(get_string)
                    .and_then(|s| s.parse().ok())
                    .unwrap_or(0);

                let job = ProcessingJob {
                    video_id,
                    job_type,
                    priority,
                    timestamp,
                };

                jobs.push((id, job));
            }
        }

        Ok(jobs)
    }

    /// Acknowledge job completion
    pub async fn ack_job(&mut self, message_id: &str) -> Result<(), anyhow::Error> {
        let _: i64 = self
            .connection
            .xack("processing-jobs", &self.consumer_group, &[message_id])
            .await?;

        tracing::debug!("Acknowledged job: {}", message_id);
        Ok(())
    }
}
