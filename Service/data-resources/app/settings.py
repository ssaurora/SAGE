from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = Field(alias="DATA_RESOURCES_DATABASE_URL")
    minio_endpoint: str = Field(alias="DATA_RESOURCES_MINIO_ENDPOINT")
    minio_access_key: str = Field(alias="DATA_RESOURCES_MINIO_ACCESS_KEY")
    minio_secret_key: str = Field(alias="DATA_RESOURCES_MINIO_SECRET_KEY")
    minio_bucket: str = Field(alias="DATA_RESOURCES_MINIO_BUCKET")
    public_api_base: str = Field(default="http://localhost:8100", alias="DATA_RESOURCES_PUBLIC_API_BASE")
    titiler_public_base: str = Field(default="http://localhost:8102", alias="DATA_RESOURCES_TITILER_PUBLIC_BASE")
    seed_manifest_path: str | None = Field(default=None, alias="DATA_RESOURCES_SEED_MANIFEST_PATH")
    poll_interval_seconds: int = Field(default=5, alias="DATA_RESOURCES_POLL_INTERVAL_SECONDS")
    cog_prefix: str = Field(default="cogs", alias="DATA_RESOURCES_COG_PREFIX")
    titiler_tile_matrix_set: str = Field(default="WebMercatorQuad", alias="DATA_RESOURCES_TITILER_TILE_MATRIX_SET")
    cors_origins: str = Field(
        default="http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173",
        alias="DATA_RESOURCES_CORS_ORIGINS",
    )

    class Config:
        populate_by_name = True


settings = Settings()


def get_cors_origins() -> list[str]:
    return [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
