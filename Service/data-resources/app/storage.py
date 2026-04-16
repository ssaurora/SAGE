from io import BytesIO

from minio import Minio

from .settings import settings


def get_storage_client():
    return Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=False,
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

