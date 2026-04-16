from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

from sqlalchemy.orm import Session

from .models import DataResourceRecord, ResourceBindingRecord


def parse_iso_datetime(value: str):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def upsert_resource(session: Session, payload: DataResourceRecord):
    existing = session.get(DataResourceRecord, payload.id)
    if existing:
        for field in (
            "name",
            "kind",
            "format",
            "storage_uri",
            "size_bytes",
            "status",
            "preview_status",
            "publish_status",
            "raster_publish_status",
            "crs",
            "bbox",
            "resolution",
            "band_count",
            "width",
            "height",
            "dtype",
            "nodata_value",
            "preview_url",
            "publish_url",
            "cog_uri",
            "tilejson_url",
            "tiles_url",
            "titiler_asset_url",
            "description",
            "source_repository",
            "source_path",
            "uploaded_at",
            "updated_at",
            "last_used_at",
            "usage_count",
            "bound_scene_count",
        ):
            setattr(existing, field, getattr(payload, field))
        existing.bindings.clear()
        for binding in payload.bindings:
            existing.bindings.append(
                ResourceBindingRecord(
                    scene_id=binding.scene_id,
                    scene_name=binding.scene_name,
                    role=binding.role,
                    bound_at=binding.bound_at,
                )
            )
        return existing
    session.add(payload)
    return payload


def seed_annual_water_yield_resources(session: Session, manifest_path: str | None):
    if not manifest_path:
        return

    manifest_file = Path(manifest_path)
    if not manifest_file.exists():
        return

    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    published_at = parse_iso_datetime(manifest["publishedAt"])

    for raster in manifest.get("rasters", []):
        resource = DataResourceRecord(
            id=raster["assetId"],
            name=raster["label"],
            kind="raster",
            format="GeoTIFF",
            storage_uri=raster["sourcePath"],
            size_bytes=0,
            status="ready",
            preview_status="ready" if raster.get("previewAvailable") else "none",
            publish_status="published" if raster.get("publishState") == "published" else "draft",
            raster_publish_status="published" if raster.get("cogPath") else "draft",
            crs=raster.get("crs"),
            bbox=raster.get("bbox"),
            resolution=None,
            band_count=1,
            width=raster.get("width"),
            height=raster.get("height"),
            dtype=raster.get("dtype"),
            preview_url=raster.get("previewPath"),
            publish_url=raster.get("coveragePath"),
            cog_uri=raster.get("cogUri") or raster.get("cogPath"),
            tilejson_url=raster.get("tileJsonUrl"),
            tiles_url=raster.get("tilesUrl"),
            titiler_asset_url=raster.get("titilerAssetUrl") or raster.get("cogPath"),
            description=raster.get("description"),
            source_repository=raster.get("sourceRepository"),
            source_path=raster.get("sourcePath"),
            uploaded_at=published_at,
            updated_at=published_at,
            last_used_at=published_at,
            usage_count=1,
            bound_scene_count=1,
        )
        resource.bindings.append(
            ResourceBindingRecord(
                scene_id=manifest["sceneId"],
                scene_name=manifest["sceneName"],
                role=raster.get("role"),
                bound_at=published_at,
            )
        )
        upsert_resource(session, resource)

    for output in manifest.get("outputs", []):
        generated_at = parse_iso_datetime(output["generatedAt"])
        resource = DataResourceRecord(
            id=output["resultId"],
            name=output["title"],
            kind="raster",
            format="Published preview",
            storage_uri=output["previewPath"],
            size_bytes=0,
            status="ready" if output.get("resultAvailable") else "processing",
            preview_status="ready" if output.get("previewPath") else "none",
            publish_status="published" if output.get("context", {}).get("publishState") == "published" else "draft",
            raster_publish_status="draft",
            crs="EPSG:4326",
            preview_url=output.get("previewPath"),
            publish_url=output.get("previewWebPath"),
            description=output.get("summary"),
            source_repository="SAGE demo publish layer",
            source_path=output.get("previewWebPath"),
            uploaded_at=generated_at,
            updated_at=generated_at,
            last_used_at=generated_at,
            usage_count=1,
            bound_scene_count=1,
        )
        resource.bindings.append(
            ResourceBindingRecord(
                scene_id=output["sceneId"],
                scene_name=output["sceneName"],
                role="result-preview",
                bound_at=generated_at,
            )
        )
        upsert_resource(session, resource)

    session.commit()
