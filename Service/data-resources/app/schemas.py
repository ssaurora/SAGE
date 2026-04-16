from datetime import datetime

from pydantic import BaseModel


class DataResourceBinding(BaseModel):
    sceneId: str
    sceneName: str
    role: str | None = None
    boundAt: datetime | None = None


class DataResource(BaseModel):
    id: str
    name: str
    kind: str
    format: str
    storageUri: str
    sizeBytes: int
    status: str
    previewStatus: str
    publishStatus: str
    crs: str | None = None
    bbox: list[float] | None = None
    resolution: str | None = None
    bandCount: int | None = None
    geometryType: str | None = None
    width: int | None = None
    height: int | None = None
    dtype: str | None = None
    nodataValue: str | None = None
    previewUrl: str | None = None
    publishUrl: str | None = None
    cogUri: str | None = None
    tileJsonUrl: str | None = None
    tilesUrl: str | None = None
    titilerAssetUrl: str | None = None
    rasterPublishStatus: str | None = None
    description: str | None = None
    sourceRepository: str | None = None
    sourcePath: str | None = None
    uploadedAt: datetime
    updatedAt: datetime
    lastUsedAt: datetime | None = None
    usageCount: int
    boundSceneCount: int
    bindings: list[DataResourceBinding] = []


class DataResourceListResponse(BaseModel):
    items: list[DataResource]


class BindResourceRequest(BaseModel):
    sceneId: str
    sceneName: str
    role: str | None = None
