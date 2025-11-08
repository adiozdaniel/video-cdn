use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub worker_id: String,
    pub database_url: String,
    pub minio_endpoint: String,
    pub minio_access_key: String,
    pub minio_secret_key: String,
    pub minio_bucket: String,
    pub redis_url: String,
    pub worker_concurrency: usize,
    pub temp_dir: String,
}

impl Config {
    pub fn from_env() -> Result<Self, anyhow::Error> {
        dotenvy::dotenv().ok();

        let worker_id = env::var("WORKER_ID").unwrap_or_else(|_| "worker-1".to_string());

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
        let minio_access_key = env::var("MINIO_ACCESS_KEY").unwrap_or_else(|_| "minioadmin".to_string());
        let minio_secret_key = env::var("MINIO_SECRET_KEY").unwrap_or_else(|_| "minioadmin123".to_string());
        let minio_bucket = env::var("MINIO_BUCKET").unwrap_or_else(|_| "videos".to_string());

        let redis_addr = env::var("REDIS_ADDR").unwrap_or_else(|_| "localhost:6379".to_string());
        let redis_url = format!("redis://{}", redis_addr);

        let worker_concurrency = env::var("WORKER_CONCURRENCY")
            .unwrap_or_else(|_| "4".to_string())
            .parse()
            .unwrap_or(4);

        let temp_dir = env::var("TEMP_DIR").unwrap_or_else(|_| "/tmp/processing".to_string());

        Ok(Config {
            worker_id,
            database_url,
            minio_endpoint,
            minio_access_key,
            minio_secret_key,
            minio_bucket,
            redis_url,
            worker_concurrency,
            temp_dir,
        })
    }
}
