use aws_config::BehaviorVersion;
use aws_sdk_s3::{Client, config::{Credentials, Region}, primitives::ByteStream};
use std::path::Path;
use tokio::fs;
use crate::config::Config;

#[derive(Clone)]
pub struct StorageClient {
    client: Client,
    bucket: String,
}

impl StorageClient {
    pub async fn new(config: &Config) -> Result<Self, anyhow::Error> {
        let endpoint_url = format!("http://{}", config.minio_endpoint);

        let credentials = Credentials::new(
            &config.minio_access_key,
            &config.minio_secret_key,
            None,
            None,
            "minio",
        );

        let s3_config = aws_sdk_s3::config::Builder::new()
            .region(Region::new("us-east-1"))
            .endpoint_url(&endpoint_url)
            .credentials_provider(credentials)
            .force_path_style(true)
            .behavior_version(BehaviorVersion::latest())
            .build();

        let client = Client::from_conf(s3_config);

        Ok(Self {
            client,
            bucket: config.minio_bucket.clone(),
        })
    }

    /// Download video from MinIO to local file
    pub async fn download_video(
        &self,
        object_key: &str,
        local_path: &Path,
    ) -> Result<(), anyhow::Error> {
        tracing::info!("Downloading {} to {:?}", object_key, local_path);

        let response = self
            .client
            .get_object()
            .bucket(&self.bucket)
            .key(object_key)
            .send()
            .await?;

        let data = response.body.collect().await?;
        fs::write(local_path, data.into_bytes()).await?;

        tracing::info!("Downloaded {} successfully", object_key);
        Ok(())
    }

    /// Upload video from local file to MinIO
    pub async fn upload_video(
        &self,
        local_path: &Path,
        object_key: &str,
        content_type: &str,
    ) -> Result<(), anyhow::Error> {
        tracing::info!("Uploading {:?} to {}", local_path, object_key);

        let body = ByteStream::from_path(local_path).await?;

        self.client
            .put_object()
            .bucket(&self.bucket)
            .key(object_key)
            .body(body)
            .content_type(content_type)
            .send()
            .await?;

        tracing::info!("Uploaded {} successfully", object_key);
        Ok(())
    }

    /// Delete object from MinIO
    pub async fn delete_object(&self, object_key: &str) -> Result<(), anyhow::Error> {
        self.client
            .delete_object()
            .bucket(&self.bucket)
            .key(object_key)
            .send()
            .await?;

        tracing::info!("Deleted {} successfully", object_key);
        Ok(())
    }
}
