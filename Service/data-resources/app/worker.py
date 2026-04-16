from __future__ import annotations

import tempfile
import time
from io import BytesIO
from pathlib import Path
from urllib.parse import quote

from sqlalchemy.orm import Session

from .database import Base, SessionLocal, engine, ensure_schema_extensions
from .models import DataResourceRecord
from .settings import settings
from .storage import ensure_bucket, get_bytes, put_bytes
from .tiff_processing import extract_tiff_metadata, generate_cog, generate_preview_png


def build_titiler_urls(asset_url: str):
    encoded = quote(asset_url, safe="")
    base = settings.titiler_public_base.rstrip("/")
    matrix_set = settings.titiler_tile_matrix_set
    return {
        "tilejson": f"{base}/cog/{matrix_set}/tilejson.json?url={encoded}",
        "tiles": f"{base}/cog/tiles/{matrix_set}" + "/{z}/{x}/{y}.png?url=" + encoded,
    }


def process_resource(session: Session, resource: DataResourceRecord):
    if not resource.storage_uri.startswith("s3://"):
        return

    resource.status = "processing"
    resource.preview_status = "generating"
    resource.raster_publish_status = "processing"
    session.commit()

    bucket_and_key = resource.storage_uri.removeprefix("s3://").split("/", 1)
    if len(bucket_and_key) != 2:
        resource.status = "failed"
        resource.preview_status = "failed"
        session.commit()
        return

    object_name = bucket_and_key[1]
    payload = get_bytes(object_name)

    with tempfile.NamedTemporaryFile(suffix=".tif", delete=False) as temp_file:
        temp_file.write(payload)
        temp_path = Path(temp_file.name)
    cog_temp_path = temp_path.with_name(f"{temp_path.stem}-cog.tif")

    try:
        metadata = extract_tiff_metadata(temp_path)
        preview = generate_preview_png(temp_path)
        generated_cog = generate_cog(temp_path, cog_temp_path)
        preview_object_name = f"preview/{resource.id}.png"
        cog_object_name = f"{settings.cog_prefix.rstrip('/')}/{resource.id}.tif"
        preview_buffer = BytesIO()
        preview.save(preview_buffer, format="PNG")
        preview_bytes = preview_buffer.getvalue()
        cog_bytes = generated_cog.read_bytes()

        put_bytes(preview_object_name, preview_bytes, "image/png")
        put_bytes(cog_object_name, cog_bytes, "image/tiff")

        cog_uri = f"s3://{settings.minio_bucket}/{cog_object_name}"
        titiler_urls = build_titiler_urls(cog_uri)

        resource.width = metadata.width
        resource.height = metadata.height
        resource.band_count = metadata.band_count
        resource.dtype = metadata.dtype
        resource.crs = metadata.crs
        resource.bbox = metadata.bbox
        resource.resolution = metadata.resolution
        resource.nodata_value = metadata.nodata_value
        resource.preview_url = f"s3://{settings.minio_bucket}/{preview_object_name}"
        resource.cog_uri = cog_uri
        resource.titiler_asset_url = cog_uri
        resource.tilejson_url = titiler_urls["tilejson"]
        resource.tiles_url = titiler_urls["tiles"]
        resource.status = "ready"
        resource.preview_status = "ready"
        resource.publish_status = "published"
        resource.raster_publish_status = "published"
        session.commit()
    except Exception:
        resource.status = "failed"
        resource.preview_status = "failed"
        resource.publish_status = "failed"
        resource.raster_publish_status = "failed"
        session.commit()
    finally:
        if temp_path.exists():
            temp_path.unlink(missing_ok=True)
        if cog_temp_path.exists():
            cog_temp_path.unlink(missing_ok=True)


def main():
    Base.metadata.create_all(bind=engine)
    ensure_schema_extensions()
    ensure_bucket()

    while True:
        with SessionLocal() as session:
            pending_resources = (
                session.query(DataResourceRecord)
                .filter(DataResourceRecord.status.in_(["uploaded", "processing"]))
                .order_by(DataResourceRecord.uploaded_at.asc())
                .all()
            )
            for resource in pending_resources:
                process_resource(session, resource)
        time.sleep(settings.poll_interval_seconds)


if __name__ == "__main__":
    main()
