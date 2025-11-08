use aws_config::BehaviorVersion;
use aws_sdk_s3::{Client, config::{Credentials, Region}, presigning::PresigningConfig};
use std::time::Duration;
use crate::config::Config;

#[derive(Clone)]
pub struct StorageClient {
    client: Client,
    presign_client: Client,
    bucket: String,
    public_endpoint: String,
    internal_endpoint: String,
}

impl StorageClient {
    pub async fn new(config: &Config) -> Result<Self, anyhow::Error> {
        let endpoint_url = format!("http://{}", config.minio_endpoint);
        let public_endpoint_url = format!("http://{}", config.minio_public_endpoint);

        let credentials = Credentials::new(
            &config.minio_access_key,
            &config.minio_secret_key,
            None,
            None,
            "minio",
        );

        // Internal client for operations
        let s3_config = aws_sdk_s3::config::Builder::new()
            .region(Region::new("us-east-1"))
            .endpoint_url(&endpoint_url)
            .credentials_provider(credentials.clone())
            .force_path_style(true)
            .behavior_version(BehaviorVersion::latest())
            .build();

        let client = Client::from_conf(s3_config);

        // Public client for presigned URLs
        let presign_s3_config = aws_sdk_s3::config::Builder::new()
            .region(Region::new("us-east-1"))
            .endpoint_url(&public_endpoint_url)
            .credentials_provider(credentials)
            .force_path_style(true)
            .behavior_version(BehaviorVersion::latest())
            .build();

        let presign_client = Client::from_conf(presign_s3_config);

        // Ensure bucket exists
        let bucket_name = config.minio_bucket.clone();
        match client.head_bucket().bucket(&bucket_name).send().await {
            Ok(_) => {
                tracing::info!("Bucket '{}' exists", bucket_name);
            }
            Err(_) => {
                tracing::info!("Creating bucket '{}'", bucket_name);
                client
                    .create_bucket()
                    .bucket(&bucket_name)
                    .send()
                    .await?;
            }
        }

        Ok(Self {
            client,
            presign_client,
            bucket: config.minio_bucket.clone(),
            public_endpoint: config.minio_public_endpoint.clone(),
            internal_endpoint: config.minio_endpoint.clone(),
        })
    }

    pub async fn generate_presigned_upload_url(
        &self,
        object_key: &str,
        expiration_seconds: u64,
    ) -> Result<String, anyhow::Error> {
        let presigning_config = PresigningConfig::expires_in(Duration::from_secs(expiration_seconds))?;

        // Use presign_client which is configured with the public endpoint
        let presigned_request = self
            .presign_client
            .put_object()
            .bucket(&self.bucket)
            .key(object_key)
            .presigned(presigning_config)
            .await?;

        Ok(presigned_request.uri().to_string())
    }
}
