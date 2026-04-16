from __future__ import annotations

import tempfile
import time
from io import BytesIO
from pathlib import Path

from sqlalchemy.orm import Session

from .database import Base, SessionLocal, engine
from .models import DataResourceRecord
from .settings import settings
from .storage import ensure_bucket, get_bytes, put_bytes
from .tiff_processing import extract_tiff_metadata, generate_preview_png


def process_resource(session: Session, resource: DataResourceRecord):
    if not resource.storage_uri.startswith("s3://"):
        return

    resource.status = "processing"
    resource.preview_status = "generating"
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

    try:
        metadata = extract_tiff_metadata(temp_path)
        preview = generate_preview_png(temp_path)
        preview_object_name = f"preview/{resource.id}.png"
        preview_buffer = BytesIO()
        preview.save(preview_buffer, format="PNG")
        preview_bytes = preview_buffer.getvalue()

        put_bytes(preview_object_name, preview_bytes, "image/png")

        resource.width = metadata.width
        resource.height = metadata.height
        resource.band_count = metadata.band_count
        resource.dtype = metadata.dtype
        resource.crs = metadata.crs
        resource.bbox = metadata.bbox
        resource.resolution = metadata.resolution
        resource.nodata_value = metadata.nodata_value
        resource.preview_url = f"s3://{settings.minio_bucket}/{preview_object_name}"
        resource.status = "ready"
        resource.preview_status = "ready"
        session.commit()
    except Exception:
        resource.status = "failed"
        resource.preview_status = "failed"
        session.commit()
    finally:
        if temp_path.exists():
            temp_path.unlink(missing_ok=True)


def main():
    Base.metadata.create_all(bind=engine)
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
