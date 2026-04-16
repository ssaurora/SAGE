from __future__ import annotations

from pathlib import Path
from uuid import uuid4
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse, StreamingResponse
from .database import Base, SessionLocal, engine
from .models import DataResourceRecord, ResourceBindingRecord
from .schemas import BindResourceRequest, DataResource, DataResourceBinding, DataResourceListResponse
from .seed import seed_annual_water_yield_resources
from .settings import settings
from .storage import ensure_bucket, get_bytes, put_bytes

app = FastAPI(title="SAGE Data Resources API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def build_preview_endpoint(resource_id: str):
    return f"{settings.public_api_base.rstrip('/')}/resources/{resource_id}/preview"


def to_schema(resource: DataResourceRecord):
    preview_url = resource.preview_url
    if preview_url and not preview_url.startswith("/"):
        preview_url = build_preview_endpoint(resource.id)

    return DataResource(
        id=resource.id,
        name=resource.name,
        kind=resource.kind,
        format=resource.format,
        storageUri=resource.storage_uri,
        sizeBytes=resource.size_bytes,
        status=resource.status,
        previewStatus=resource.preview_status,
        publishStatus=resource.publish_status,
        crs=resource.crs,
        bbox=resource.bbox,
        resolution=resource.resolution,
        bandCount=resource.band_count,
        geometryType=resource.geometry_type,
        width=resource.width,
        height=resource.height,
        dtype=resource.dtype,
        nodataValue=resource.nodata_value,
        previewUrl=preview_url,
        publishUrl=resource.publish_url,
        description=resource.description,
        sourceRepository=resource.source_repository,
        sourcePath=resource.source_path,
        uploadedAt=resource.uploaded_at,
        updatedAt=resource.updated_at,
        lastUsedAt=resource.last_used_at,
        usageCount=resource.usage_count,
        boundSceneCount=resource.bound_scene_count,
        bindings=[
            DataResourceBinding(
                sceneId=binding.scene_id,
                sceneName=binding.scene_name,
                role=binding.role,
                boundAt=binding.bound_at,
            )
            for binding in resource.bindings
        ],
    )


@app.on_event("startup")
def startup():
    Base.metadata.create_all(bind=engine)
    ensure_bucket()
    with SessionLocal() as session:
        seed_annual_water_yield_resources(session, settings.seed_manifest_path)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/resources", response_model=DataResourceListResponse)
def list_resources():
    with SessionLocal() as session:
        items = session.query(DataResourceRecord).order_by(DataResourceRecord.updated_at.desc()).all()
        return DataResourceListResponse(items=[to_schema(item) for item in items])


@app.get("/resources/{resource_id}", response_model=DataResource)
def get_resource(resource_id: str):
    with SessionLocal() as session:
        resource = session.get(DataResourceRecord, resource_id)
        if not resource:
            raise HTTPException(status_code=404, detail="Resource not found")
        return to_schema(resource)


@app.post("/resources/upload", response_model=DataResource)
async def upload_resource(file: UploadFile = File(...)):
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in {".tif", ".tiff"}:
        raise HTTPException(status_code=400, detail="Only .tif and .tiff uploads are supported in this MVP.")

    resource_id = uuid4().hex
    payload = await file.read()
    object_name = f"raw/{resource_id}/{file.filename}"
    put_bytes(object_name, payload, file.content_type or "image/tiff")

    with SessionLocal() as session:
        resource = DataResourceRecord(
            id=resource_id,
            name=Path(file.filename or resource_id).stem.replace("_", " "),
            kind="raster",
            format="GeoTIFF",
            storage_uri=f"s3://{settings.minio_bucket}/{object_name}",
            size_bytes=len(payload),
            status="uploaded",
            preview_status="none",
            publish_status="draft",
            source_repository="User upload",
            source_path=file.filename,
        )
        session.add(resource)
        session.commit()
        session.refresh(resource)
        return to_schema(resource)


@app.post("/resources/{resource_id}/preview/regenerate", response_model=DataResource)
def regenerate_preview(resource_id: str):
    with SessionLocal() as session:
        resource = session.get(DataResourceRecord, resource_id)
        if not resource:
            raise HTTPException(status_code=404, detail="Resource not found")
        resource.status = "processing"
        resource.preview_status = "generating"
        session.commit()
        session.refresh(resource)
        return to_schema(resource)


@app.post("/resources/{resource_id}/bindings", response_model=DataResource)
def bind_resource(resource_id: str, payload: BindResourceRequest):
    with SessionLocal() as session:
        resource = session.get(DataResourceRecord, resource_id)
        if not resource:
            raise HTTPException(status_code=404, detail="Resource not found")

        existing = (
            session.query(ResourceBindingRecord)
            .filter(
                ResourceBindingRecord.resource_id == resource_id,
                ResourceBindingRecord.scene_id == payload.sceneId,
            )
            .first()
        )
        if not existing:
            resource.bindings.append(
                ResourceBindingRecord(
                    scene_id=payload.sceneId,
                    scene_name=payload.sceneName,
                    role=payload.role,
                )
            )
            resource.bound_scene_count += 1
        session.commit()
        session.refresh(resource)
        return to_schema(resource)


@app.get("/resources/{resource_id}/preview")
def get_preview(resource_id: str):
    with SessionLocal() as session:
        resource = session.get(DataResourceRecord, resource_id)
        if not resource or not resource.preview_url:
            raise HTTPException(status_code=404, detail="Preview not available")
        if resource.preview_url.startswith("/"):
            return RedirectResponse(resource.preview_url)

        if resource.preview_url.startswith("s3://"):
            parts = resource.preview_url.removeprefix("s3://").split("/", 1)
            if len(parts) != 2:
                raise HTTPException(status_code=500, detail="Invalid preview URI")
            object_name = parts[1]
            return StreamingResponse(iter([get_bytes(object_name)]), media_type="image/png")

        raise HTTPException(status_code=404, detail="Preview location not supported")
