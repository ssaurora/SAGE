from __future__ import annotations

from app.schemas import MetadataCatalogFact


def normalize_catalog_facts(facts: list[MetadataCatalogFact]) -> list[MetadataCatalogFact]:
    normalized: list[MetadataCatalogFact] = []
    for fact in facts:
        asset_id = fact.asset_id.strip()
        if not asset_id:
            continue
        role_candidates = sorted({role.strip() for role in fact.logical_role_candidates if role and role.strip()})
        normalized.append(
            MetadataCatalogFact(
                asset_id=asset_id,
                logical_role_candidates=role_candidates,
                file_type=_normalize_optional_text(fact.file_type),
                crs=_normalize_optional_text(fact.crs),
                extent=_normalize_optional_text(fact.extent),
                resolution=_normalize_optional_text(fact.resolution),
                nodata_info=_normalize_optional_text(fact.nodata_info),
                source=_normalize_optional_text(fact.source) or "unknown",
                checksum_version=_normalize_optional_text(fact.checksum_version),
                availability_status=_normalize_status(fact.availability_status),
                blacklist_flag=bool(fact.blacklist_flag),
            )
        )
    return normalized


def build_catalog_summary(
    facts: list[MetadataCatalogFact],
    *,
    bound_roles: set[str],
) -> dict[str, str | int | bool]:
    normalized = normalize_catalog_facts(facts)
    ready_facts = [
        fact
        for fact in normalized
        if fact.availability_status == "READY" and not fact.blacklist_flag
    ]
    blacklisted_count = sum(1 for fact in normalized if fact.blacklist_flag)
    ready_roles = {
        role_name
        for fact in ready_facts
        for role_name in fact.logical_role_candidates
    }
    materialized_roles = bound_roles & ready_roles
    return {
        "catalog_asset_count": len(normalized),
        "catalog_ready_asset_count": len(ready_facts),
        "catalog_blacklisted_asset_count": blacklisted_count,
        "catalog_role_coverage_count": len(ready_roles),
        "catalog_materialized_role_count": len(materialized_roles),
        "catalog_used_for_materialization": len(materialized_roles) > 0,
        "catalog_source": "metadata_catalog_facts" if normalized else "none",
    }


def _normalize_optional_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _normalize_status(value: str) -> str:
    normalized = value.strip().upper()
    return normalized or "UNKNOWN"
