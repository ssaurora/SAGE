from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
import tifffile
from PIL import Image
from pyproj import CRS, Transformer


@dataclass
class TiffMetadata:
    width: int
    height: int
    band_count: int
    dtype: str
    crs: str | None
    bbox: list[float] | None
    resolution: str | None
    nodata_value: str | None


def _extract_bbox_and_crs(page: tifffile.TiffPage):
    model_scale = page.tags.get("ModelPixelScaleTag")
    model_tiepoint = page.tags.get("ModelTiepointTag")
    geokey = page.geotiff_tags
    if not model_scale or not model_tiepoint:
        return None, None, None

    scale = model_scale.value
    tie = model_tiepoint.value
    width = page.imagewidth
    height = page.imagelength
    min_x = tie[3]
    max_y = tie[4]
    max_x = min_x + (width * scale[0])
    min_y = max_y - (height * scale[1])

    epsg = None
    if geokey:
        epsg = geokey.get("ProjectedCSTypeGeoKey") or geokey.get("GeographicTypeGeoKey")
    crs_name = f"EPSG:{epsg}" if epsg else None

    if epsg:
        source_crs = CRS.from_epsg(epsg)
        target_crs = CRS.from_epsg(4326)
        transformer = Transformer.from_crs(source_crs, target_crs, always_xy=True)
        min_lon, min_lat = transformer.transform(min_x, min_y)
        max_lon, max_lat = transformer.transform(max_x, max_y)
        bbox = [round(min_lon, 6), round(min_lat, 6), round(max_lon, 6), round(max_lat, 6)]
        resolution = f"{scale[0]:.4f} x {scale[1]:.4f} {source_crs.axis_info[0].unit_name if source_crs.axis_info else 'units'}"
        return bbox, crs_name, resolution

    return None, crs_name, None


def extract_tiff_metadata(path: Path):
    with tifffile.TiffFile(path) as tif:
        page = tif.pages[0]
        bbox, crs_name, resolution = _extract_bbox_and_crs(page)
        nodata = page.tags.get("GDAL_NODATA")
        band_count = page.samplesperpixel if page.samplesperpixel else 1
        return TiffMetadata(
            width=page.imagewidth,
            height=page.imagelength,
            band_count=band_count,
            dtype=str(page.dtype),
            crs=crs_name,
            bbox=bbox,
            resolution=resolution,
            nodata_value=str(nodata.value) if nodata else None,
        )


def _normalize_array(array: np.ndarray):
    values = array.astype(np.float32)
    valid = np.isfinite(values)
    if not np.any(valid):
        return np.zeros_like(values, dtype=np.uint8)
    low, high = np.percentile(values[valid], [2, 98])
    if np.isclose(low, high):
        high = low + 1
    scaled = np.clip((values - low) / (high - low), 0, 1)
    return (scaled * 255).astype(np.uint8)


def generate_preview_png(path: Path):
    array = tifffile.imread(path)
    if array.ndim == 3:
        array = array[0]
    normalized = _normalize_array(np.asarray(array))
    preview = Image.fromarray(normalized, mode="L").convert("RGB")
    max_size = (1024, 1024)
    preview.thumbnail(max_size)
    return preview
