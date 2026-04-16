from sqlalchemy import create_engine, text
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .settings import settings


engine = create_engine(settings.database_url, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)


class Base(DeclarativeBase):
    pass


def ensure_schema_extensions():
    statements = [
        "ALTER TABLE data_resources ADD COLUMN IF NOT EXISTS cog_uri TEXT",
        "ALTER TABLE data_resources ADD COLUMN IF NOT EXISTS tilejson_url TEXT",
        "ALTER TABLE data_resources ADD COLUMN IF NOT EXISTS tiles_url TEXT",
        "ALTER TABLE data_resources ADD COLUMN IF NOT EXISTS titiler_asset_url TEXT",
        "ALTER TABLE data_resources ADD COLUMN IF NOT EXISTS raster_publish_status VARCHAR(32)",
    ]
    with engine.begin() as connection:
        for statement in statements:
            connection.execute(text(statement))
