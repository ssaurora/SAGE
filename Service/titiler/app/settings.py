from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    public_base_url: str = Field(default="http://localhost:8102", alias="TITILER_PUBLIC_BASE_URL")
    cors_origins: str = Field(
        default="http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173",
        alias="TITILER_CORS_ORIGINS",
    )
    minio_endpoint: str = Field(default="http://data-resources-minio:9000", alias="TITILER_MINIO_ENDPOINT")
    minio_access_key: str = Field(default="sage", alias="TITILER_MINIO_ACCESS_KEY")
    minio_secret_key: str = Field(default="sage-resource-secret", alias="TITILER_MINIO_SECRET_KEY")
    tile_matrix_set: str = Field(default="WebMercatorQuad", alias="TITILER_TILE_MATRIX_SET")

    class Config:
        populate_by_name = True


settings = Settings()


def get_cors_origins() -> list[str]:
    return [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
