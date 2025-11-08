use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub minio_endpoint: String,
    pub minio_public_endpoint: String,
    pub minio_access_key: String,
    pub minio_secret_key: String,
    pub minio_bucket: String,
    pub redis_url: String,
    pub server_port: u16,
}

impl Config {
    pub fn from_env() -> Result<Self, anyhow::Error> {
        // Load .env file if it exists
        dotenvy::dotenv().ok();

        let db_host = env::var("DB_HOST").unwrap_or_else(|_| "localhost".to_string());
        let db_port = env::var("DB_PORT").unwrap_or_else(|_| "5432".to_string());
        let db_user = env::var("DB_USER").unwrap_or_else(|_| "cdn_app".to_string());
        let db_password = env::var("DB_PASSWORD").unwrap_or_else(|_| "cdn_password_2024".to_string());
        let db_name = env::var("DB_NAME").unwrap_or_else(|_| "cdn".to_string());

        let database_url = format!(
            "postgres://{}:{}@{}:{}/{}",
            db_user, db_password, db_host, db_port, db_name
        );

        let minio_endpoint = env::var("MINIO_ENDPOINT").unwrap_or_else(|_| "localhost:9000".to_string());
        let minio_public_endpoint = env::var("MINIO_PUBLIC_ENDPOINT").unwrap_or_else(|_| minio_endpoint.clone());
        let minio_access_key = env::var("MINIO_ACCESS_KEY").unwrap_or_else(|_| "minioadmin".to_string());
        let minio_secret_key = env::var("MINIO_SECRET_KEY").unwrap_or_else(|_| "minioadmin123".to_string());
        let minio_bucket = env::var("MINIO_BUCKET").unwrap_or_else(|_| "videos".to_string());

        let redis_addr = env::var("REDIS_ADDR").unwrap_or_else(|_| "localhost:6379".to_string());
        let redis_url = format!("redis://{}", redis_addr);

        let server_port = env::var("SERVER_PORT")
            .unwrap_or_else(|_| "8080".to_string())
            .parse()
            .unwrap_or(8080);

        Ok(Config {
            database_url,
            minio_endpoint,
            minio_public_endpoint,
            minio_access_key,
            minio_secret_key,
            minio_bucket,
            redis_url,
            server_port,
        })
    }
}
