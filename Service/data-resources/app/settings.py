from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = Field(alias="DATA_RESOURCES_DATABASE_URL")
    minio_endpoint: str = Field(alias="DATA_RESOURCES_MINIO_ENDPOINT")
    minio_access_key: str = Field(alias="DATA_RESOURCES_MINIO_ACCESS_KEY")
    minio_secret_key: str = Field(alias="DATA_RESOURCES_MINIO_SECRET_KEY")
    minio_bucket: str = Field(alias="DATA_RESOURCES_MINIO_BUCKET")
    public_api_base: str = Field(default="http://localhost:8100", alias="DATA_RESOURCES_PUBLIC_API_BASE")
    seed_manifest_path: str | None = Field(default=None, alias="DATA_RESOURCES_SEED_MANIFEST_PATH")
    poll_interval_seconds: int = Field(default=5, alias="DATA_RESOURCES_POLL_INTERVAL_SECONDS")

    class Config:
        populate_by_name = True


settings = Settings()

