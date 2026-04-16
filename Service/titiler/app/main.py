from __future__ import annotations

from io import BytesIO
from urllib.parse import unquote

import morecantile
import rasterio
from PIL import Image
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from rio_tiler.errors import TileOutsideBounds
from rio_tiler.io import COGReader
from titiler.core.errors import DEFAULT_STATUS_CODES, add_exception_handlers

from .settings import get_cors_origins, settings

app = FastAPI(title="SAGE TiTiler")
app.add_middleware(
    CORSMiddleware,
    allow_origins=get_cors_origins(),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
add_exception_handlers(app, DEFAULT_STATUS_CODES)


def normalize_asset_url(url: str) -> str:
    return unquote(url)


def gdal_env_options() -> dict[str, str | bool]:
    endpoint = settings.minio_endpoint
    if endpoint.startswith("http://"):
        endpoint = endpoint.removeprefix("http://")
    elif endpoint.startswith("https://"):
        endpoint = endpoint.removeprefix("https://")
    secure = "YES" if settings.minio_endpoint.startswith("https://") else "NO"
    return {
        "AWS_ACCESS_KEY_ID": settings.minio_access_key,
        "AWS_SECRET_ACCESS_KEY": settings.minio_secret_key,
        "AWS_S3_ENDPOINT": endpoint,
        "AWS_HTTPS": secure,
        "AWS_VIRTUAL_HOSTING": False,
    }


def requires_s3_env(url: str) -> bool:
    asset_url = normalize_asset_url(url)
    return asset_url.startswith("s3://") or asset_url.startswith("/vsis3/")


def with_rasterio_env(url: str):
    if requires_s3_env(url):
        return rasterio.Env(**gdal_env_options())
    return rasterio.Env()


def open_reader(url: str):
    return COGReader(normalize_asset_url(url))


def empty_png(tilesize: int = 256) -> bytes:
    image = Image.new("RGBA", (tilesize, tilesize), (0, 0, 0, 0))
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/cog/{tile_matrix_set_id}/tilejson.json")
def tilejson(
    tile_matrix_set_id: str,
    url: str = Query(...),
):
    if tile_matrix_set_id != settings.tile_matrix_set:
        raise HTTPException(status_code=404, detail="Unsupported tile matrix set")

    with with_rasterio_env(url):
        with open_reader(url) as reader:
            bounds = list(reader.get_geographic_bounds("EPSG:4326"))
            minzoom = reader.minzoom
            maxzoom = reader.maxzoom

    encoded_url = url
    tiles = [
        f"{settings.public_base_url.rstrip('/')}/cog/tiles/{tile_matrix_set_id}/{{z}}/{{x}}/{{y}}.png?url={encoded_url}"
    ]
    return JSONResponse(
        {
            "tilejson": "3.0.0",
            "name": "SAGE raster tiles",
            "scheme": "xyz",
            "tiles": tiles,
            "bounds": bounds,
            "center": [
                round((bounds[0] + bounds[2]) / 2, 6),
                round((bounds[1] + bounds[3]) / 2, 6),
                minzoom,
            ],
            "minzoom": minzoom,
            "maxzoom": maxzoom,
        }
    )


@app.get("/cog/tiles/{tile_matrix_set_id}/{z}/{x}/{y}.png")
def tile(
    tile_matrix_set_id: str,
    z: int,
    x: int,
    y: int,
    url: str = Query(...),
):
    if tile_matrix_set_id != settings.tile_matrix_set:
        raise HTTPException(status_code=404, detail="Unsupported tile matrix set")

    tms = morecantile.tms.get(tile_matrix_set_id)
    with with_rasterio_env(url):
        with open_reader(url) as reader:
            try:
                image = reader.tile(x, y, z, tilesize=256, indexes=None, expression=None, buffer=None)
                content = image.render(img_format="PNG")
            except TileOutsideBounds:
                content = empty_png(256)
    return Response(content, media_type="image/png")


@app.get("/cog/preview.png")
def preview(
    url: str = Query(...),
    max_size: int = Query(default=1024, ge=64, le=4096),
):
    with with_rasterio_env(url):
        with open_reader(url) as reader:
            image = reader.preview(max_size=max_size)
            content = image.render(img_format="PNG")
    return Response(content, media_type="image/png")
