from io import BytesIO

from minio import Minio

from .settings import settings


def _normalized_minio_endpoint() -> tuple[str, bool]:
    endpoint = settings.minio_endpoint.strip()
    secure = endpoint.startswith("https://")
    if endpoint.startswith("http://"):
        endpoint = endpoint.removeprefix("http://")
    elif endpoint.startswith("https://"):
        endpoint = endpoint.removeprefix("https://")
    return endpoint, secure


def get_storage_client():
    endpoint, secure = _normalized_minio_endpoint()
    return Minio(
        endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=secure,
    )


def ensure_bucket():
    client = get_storage_client()
    if not client.bucket_exists(settings.minio_bucket):
        client.make_bucket(settings.minio_bucket)


def put_bytes(object_name: str, payload: bytes, content_type: str):
    client = get_storage_client()
    client.put_object(
        settings.minio_bucket,
        object_name,
        BytesIO(payload),
        len(payload),
        content_type=content_type,
    )


def get_bytes(object_name: str) -> bytes:
    client = get_storage_client()
    response = client.get_object(settings.minio_bucket, object_name)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()
