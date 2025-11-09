use rdkafka::config::ClientConfig;
use rdkafka::consumer::{Consumer, StreamConsumer};
use rdkafka::producer::{FutureProducer, FutureRecord};
use rdkafka::Message;
use std::time::Duration;
use crate::models::{ProfileJobMessage, CompletionEvent};

pub struct JobQueue {
    consumer: StreamConsumer,
    producer: FutureProducer,
    profile: String,
}

impl JobQueue {
    pub async fn new(
        kafka_brokers: &str,
        group_id: &str,
        profile: &str,
    ) -> Result<Self, anyhow::Error> {
        let consumer: StreamConsumer = ClientConfig::new()
            .set("bootstrap.servers", kafka_brokers)
            .set("group.id", group_id)
            .set("enable.auto.commit", "true")
            .set("auto.offset.reset", "earliest")
            .set("session.timeout.ms", "6000")
            .create()?;

        let topic = format!("processing-jobs-{}", profile);
        consumer.subscribe(&[&topic])?;

        tracing::info!("Subscribed to Kafka topic: {}", topic);

        let producer: FutureProducer = ClientConfig::new()
            .set("bootstrap.servers", kafka_brokers)
            .set("message.timeout.ms", "5000")
            .set("acks", "all")
            .set("retries", "3")
            .set("enable.idempotence", "true")
            .create()?;

        Ok(Self {
            consumer,
            producer,
            profile: profile.to_string(),
        })
    }

    /// Read profile jobs from Kafka
    pub async fn read_profile_jobs(&self) -> Result<Vec<ProfileJobMessage>, anyhow::Error> {
        let mut jobs = Vec::new();

        // Poll for messages with 5 second timeout
        match tokio::time::timeout(
            Duration::from_secs(5),
            self.consumer.recv()
        ).await {
            Ok(Ok(message)) => {
                if let Some(payload) = message.payload() {
                    let job: ProfileJobMessage = serde_json::from_slice(payload)?;
                    tracing::debug!("Received job from partition {}, offset {}: videoId={}",
                        message.partition(), message.offset(), job.video_id);
                    jobs.push(job);
                }
            }
            Ok(Err(e)) => {
                tracing::error!("Kafka consumer error: {}", e);
                return Err(e.into());
            }
            Err(_) => {
                // Timeout - no messages available, return empty vec
            }
        }

        Ok(jobs)
    }

    /// Publish completion event to orchestrator
    pub async fn publish_completion(
        &self,
        event: &CompletionEvent,
    ) -> Result<(), anyhow::Error> {
        let json = serde_json::to_string(event)?;
        let topic = "processing-jobs-completed";

        let record = FutureRecord::to(topic)
            .key(&event.video_id)
            .payload(&json);

        match self.producer.send(record, Duration::from_secs(5)).await {
            Ok((partition, offset)) => {
                tracing::info!(
                    "Published completion event for {} {} to partition {} offset {} (status: {})",
                    event.video_id,
                    event.profile,
                    partition,
                    offset,
                    event.status
                );
                Ok(())
            }
            Err((e, _)) => {
                tracing::error!("Failed to publish completion event: {}", e);
                Err(e.into())
            }
        }
    }
}
