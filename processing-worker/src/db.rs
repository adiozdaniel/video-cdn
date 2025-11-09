use sqlx::{PgPool, postgres::PgPoolOptions, query};
use std::time::Duration;
use uuid::Uuid;

pub async fn create_pool(database_url: &str) -> Result<PgPool, sqlx::Error> {
    PgPoolOptions::new()
        .max_connections(20)
        .min_connections(2)
        .acquire_timeout(Duration::from_secs(5))
        .connect(database_url)
        .await
}

pub async fn update_job_status(
    pool: &PgPool,
    video_id: Uuid,
    status: &str,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE processing_jobs
         SET status = $1, updated_at = NOW()
         WHERE video_id = $2"
    )
    .bind(status)
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn update_video_status(
    pool: &PgPool,
    video_id: Uuid,
    status: &str,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE videos
         SET status = $1
         WHERE id = $2"
    )
    .bind(status)
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn mark_job_failed(
    pool: &PgPool,
    video_id: Uuid,
    error_message: &str,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE processing_jobs
         SET status = 'FAILED', error_message = $1, updated_at = NOW()
         WHERE video_id = $2"
    )
    .bind(error_message)
    .bind(video_id)
    .execute(pool)
    .await?;

    query(
        "UPDATE videos
         SET status = 'FAILED'
         WHERE id = $1"
    )
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn mark_video_playable(
    pool: &PgPool,
    video_id: Uuid,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE videos
         SET status = 'PLAYABLE'
         WHERE id = $1"
    )
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn mark_job_completed(
    pool: &PgPool,
    video_id: Uuid,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE processing_jobs
         SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW()
         WHERE video_id = $1"
    )
    .bind(video_id)
    .execute(pool)
    .await?;

    query(
        "UPDATE videos
         SET status = 'READY'
         WHERE id = $1"
    )
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn update_job_progress(
    pool: &PgPool,
    video_id: Uuid,
    progress: i32,
) -> Result<(), sqlx::Error> {
    query(
        "UPDATE processing_jobs
         SET progress = $1, updated_at = NOW()
         WHERE video_id = $2"
    )
    .bind(progress)
    .bind(video_id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn get_video_filename(
    pool: &PgPool,
    video_id: Uuid,
) -> Result<String, sqlx::Error> {
    let row: (String,) = sqlx::query_as(
        "SELECT filename FROM videos WHERE id = $1"
    )
    .bind(video_id)
    .fetch_one(pool)
    .await?;

    Ok(row.0)
}
