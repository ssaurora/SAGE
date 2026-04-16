from datetime import datetime, timezone
from uuid import uuid4

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def utcnow():
    return datetime.now(timezone.utc)


class DataResourceRecord(Base):
    __tablename__ = "data_resources"

    id: Mapped[str] = mapped_column(String(128), primary_key=True, default=lambda: uuid4().hex)
    name: Mapped[str] = mapped_column(String(255))
    kind: Mapped[str] = mapped_column(String(32))
    format: Mapped[str] = mapped_column(String(64))
    storage_uri: Mapped[str] = mapped_column(Text)
    size_bytes: Mapped[int] = mapped_column(BigInteger, default=0)
    status: Mapped[str] = mapped_column(String(32), default="uploaded")
    preview_status: Mapped[str] = mapped_column(String(32), default="none")
    publish_status: Mapped[str] = mapped_column(String(32), default="draft")
    crs: Mapped[str | None] = mapped_column(String(64), nullable=True)
    bbox: Mapped[list[float] | None] = mapped_column(JSONB, nullable=True)
    resolution: Mapped[str | None] = mapped_column(String(128), nullable=True)
    band_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    geometry_type: Mapped[str | None] = mapped_column(String(64), nullable=True)
    width: Mapped[int | None] = mapped_column(Integer, nullable=True)
    height: Mapped[int | None] = mapped_column(Integer, nullable=True)
    dtype: Mapped[str | None] = mapped_column(String(64), nullable=True)
    nodata_value: Mapped[str | None] = mapped_column(String(64), nullable=True)
    preview_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    publish_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    cog_uri: Mapped[str | None] = mapped_column(Text, nullable=True)
    tilejson_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    tiles_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    titiler_asset_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    raster_publish_status: Mapped[str | None] = mapped_column(String(32), nullable=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    source_repository: Mapped[str | None] = mapped_column(String(255), nullable=True)
    source_path: Mapped[str | None] = mapped_column(Text, nullable=True)
    uploaded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)
    last_used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    usage_count: Mapped[int] = mapped_column(Integer, default=0)
    bound_scene_count: Mapped[int] = mapped_column(Integer, default=0)

    bindings: Mapped[list["ResourceBindingRecord"]] = relationship(
        back_populates="resource",
        cascade="all, delete-orphan",
    )


class ResourceBindingRecord(Base):
    __tablename__ = "resource_bindings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    resource_id: Mapped[str] = mapped_column(ForeignKey("data_resources.id", ondelete="CASCADE"))
    scene_id: Mapped[str] = mapped_column(String(255))
    scene_name: Mapped[str] = mapped_column(String(255))
    role: Mapped[str | None] = mapped_column(String(128), nullable=True)
    bound_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    resource: Mapped[DataResourceRecord] = relationship(back_populates="bindings")
