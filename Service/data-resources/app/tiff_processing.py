from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
import rasterio
import tifffile
from PIL import Image
from pyproj import CRS, Transformer
from rio_cogeo.cogeo import cog_translate
from rio_cogeo.profiles import cog_profiles


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


def _parse_nodata_value(raw_value):
    if raw_value is None:
        return None
    if isinstance(raw_value, bytes):
        raw_value = raw_value.decode("utf-8", errors="ignore")
    if isinstance(raw_value, str):
        raw_value = raw_value.strip()
        if raw_value == "":
            return None
        for caster in (int, float):
            try:
                return caster(raw_value)
            except ValueError:
                continue
        return raw_value
    return raw_value


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
            nodata_value=str(_parse_nodata_value(nodata.value)) if nodata else None,
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


def _read_tiff_array(path: Path):
    with tifffile.TiffFile(path) as tif:
        page = tif.pages[0]
        array = page.asarray()
        nodata_tag = page.tags.get("GDAL_NODATA")
        nodata_value = _parse_nodata_value(nodata_tag.value) if nodata_tag else None
        return array, nodata_value


def _as_channel_last(array: np.ndarray):
    if array.ndim != 3:
        return array
    if array.shape[-1] in (1, 2, 3, 4):
        return array
    if array.shape[0] in (1, 2, 3, 4):
        return np.moveaxis(array, 0, -1)
    return array


def _build_valid_mask(array: np.ndarray, nodata_value):
    base_array = np.asarray(array)

    if np.ma.isMaskedArray(array):
        valid_mask = ~np.ma.getmaskarray(array)
        base_array = np.ma.getdata(array)
    else:
        valid_mask = np.ones(base_array.shape, dtype=bool)

    if np.issubdtype(base_array.dtype, np.floating):
        valid_mask &= np.isfinite(base_array)

    if nodata_value is not None:
        try:
            valid_mask &= base_array != np.asarray(nodata_value, dtype=base_array.dtype)
        except (TypeError, ValueError):
            valid_mask &= base_array != nodata_value

    if valid_mask.ndim == 3:
        valid_mask = np.any(valid_mask, axis=-1)

    return valid_mask, base_array


def _normalize_channel(channel: np.ndarray, valid_mask: np.ndarray):
    values = channel.astype(np.float32)
    valid = valid_mask & np.isfinite(values)
    output = np.zeros(channel.shape, dtype=np.uint8)
    if not np.any(valid):
        return output
    low, high = np.percentile(values[valid], [2, 98])
    if np.isclose(low, high):
        high = low + 1
    scaled = np.clip((values - low) / (high - low), 0, 1)
    output[valid] = (scaled[valid] * 255).astype(np.uint8)
    return output


def _scrub_transparent_rgb(image: Image.Image):
    rgba = np.asarray(image.convert("RGBA")).copy()
    transparent = rgba[..., 3] == 0
    rgba[transparent, :3] = 0
    return Image.fromarray(rgba, mode="RGBA")


def generate_preview_png(path: Path):
    array, nodata_value = _read_tiff_array(path)
    channel_last = _as_channel_last(np.asarray(array))
    valid_mask, base_array = _build_valid_mask(channel_last, nodata_value)
    alpha = np.where(valid_mask, 255, 0).astype(np.uint8)

    if base_array.ndim == 2:
        normalized = _normalize_channel(base_array, valid_mask)
        rgba = np.stack([normalized, normalized, normalized, alpha], axis=-1)
        preview = Image.fromarray(rgba, mode="RGBA")
    elif base_array.ndim == 3:
        channel_count = base_array.shape[-1]
        if channel_count >= 4:
            rgb = base_array[..., :3]
            source_alpha = base_array[..., 3]
            if np.issubdtype(rgb.dtype, np.floating):
                rgb_uint8 = np.stack(
                    [_normalize_channel(rgb[..., index], valid_mask) for index in range(3)],
                    axis=-1,
                )
            else:
                rgb_uint8 = np.clip(rgb, 0, 255).astype(np.uint8)
            if np.issubdtype(source_alpha.dtype, np.floating):
                source_alpha_uint8 = _normalize_channel(source_alpha, valid_mask)
            else:
                source_alpha_uint8 = np.clip(source_alpha, 0, 255).astype(np.uint8)
            combined_alpha = np.where(valid_mask, source_alpha_uint8, 0).astype(np.uint8)
            rgba = np.dstack([rgb_uint8, combined_alpha])
            preview = Image.fromarray(rgba, mode="RGBA")
        else:
            rgb_uint8 = np.stack(
                [_normalize_channel(base_array[..., index], valid_mask) for index in range(min(channel_count, 3))],
                axis=-1,
            )
            if rgb_uint8.shape[-1] == 1:
                rgb_uint8 = np.repeat(rgb_uint8, 3, axis=-1)
            elif rgb_uint8.shape[-1] == 2:
                rgb_uint8 = np.concatenate([rgb_uint8, rgb_uint8[..., :1]], axis=-1)
            rgba = np.dstack([rgb_uint8[..., :3], alpha])
            preview = Image.fromarray(rgba, mode="RGBA")
    else:
        raise ValueError("Unsupported TIFF dimensions for preview generation.")

    max_size = (1024, 1024)
    preview.thumbnail(max_size)
    return _scrub_transparent_rgb(preview)


def generate_cog(path: Path, destination: Path):
    destination.parent.mkdir(parents=True, exist_ok=True)
    profile = cog_profiles.get("deflate")
    profile.update(
        {
            "blockxsize": 512,
            "blockysize": 512,
        }
    )
    config = {
        "GDAL_TIFF_INTERNAL_MASK": True,
        "GDAL_NUM_THREADS": "ALL_CPUS",
    }
    with rasterio.open(path) as src:
        cog_translate(
            src,
            destination,
            profile,
            config=config,
            quiet=True,
            in_memory=False,
        )
    return destination
