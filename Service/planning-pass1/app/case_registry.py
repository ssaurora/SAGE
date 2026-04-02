from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath


REGISTRY_VERSION = "water_yield_case_registry_v1"


@dataclass(frozen=True)
class WaterYieldCaseDescriptor:
    case_id: str
    display_name: str
    aliases: tuple[str, ...]
    intent_signals: tuple[str, ...]
    required_roles: tuple[str, ...]
    provider_key: str
    runtime_profile: str
    sample_root: str
    default_args: dict[str, str | int | float | bool]
    descriptor_version: str
    executable: bool

    def matches(self, lower_query: str) -> bool:
        haystack = lower_query.strip().lower()
        if not haystack:
            return False
        return any(token in haystack for token in (*self.aliases, *self.intent_signals, self.case_id))

    def build_args_draft(
        self,
        *,
        task_id: str,
        analysis_template: str,
        runtime_mode: str,
        contract_mode: str,
        missing_roles: set[str],
    ) -> dict[str, str | int | float | bool]:
        args_draft: dict[str, str | int | float | bool] = {
            "analysis_template": analysis_template,
            "case_id": self.case_id,
            "case_descriptor_version": self.descriptor_version,
            "case_profile_version": self.descriptor_version,
            "contract_mode": contract_mode,
            "runtime_mode": runtime_mode,
            "workspace_dir": f"/workspace/output/{task_id.replace('/', '_')}",
            "results_suffix": str(self.default_args.get("results_suffix", self.case_id)),
            "sample_data_root": self.sample_root,
        }
        args_draft.update(self.default_args)
        path_map = {
            "watersheds": "watersheds_path",
            "sub_watersheds": "sub_watersheds_path",
            "lulc": "lulc_path",
            "biophysical_table": "biophysical_table_path",
            "precipitation": "precipitation_path",
            "eto": "eto_path",
            "depth_to_root_restricting_layer": "depth_to_root_restricting_layer_path",
            "plant_available_water_content": "plant_available_water_content_path",
            "invest_datastack": "invest_datastack_path",
            "demand_table": "demand_table_path",
            "valuation_table": "valuation_table_path",
        }
        for logical_name, arg_key in path_map.items():
            if logical_name in missing_roles:
                continue
            file_name = self.default_args.get(logical_name)
            if not isinstance(file_name, str) or not file_name.strip():
                continue
            args_draft[arg_key] = str(PurePosixPath(self.sample_root) / file_name)
        return args_draft


_WATER_YIELD_CASES: tuple[WaterYieldCaseDescriptor, ...] = (
    WaterYieldCaseDescriptor(
        case_id="annual_water_yield_gura",
        display_name="Annual Water Yield - Gura",
        aliases=("gura", "gura case", "annual water yield gura", "gura annual water yield"),
        intent_signals=("ethiopia gura", "gura watershed", "gura basin"),
        required_roles=(
            "watersheds",
            "lulc",
            "biophysical_table",
            "precipitation",
            "eto",
        ),
        provider_key="planning-pass1-invest-local",
        runtime_profile="docker-invest-real",
        sample_root="/sample-data/Annual_Water_Yield",
        default_args={
            "results_suffix": "gura",
            "seasonality_constant": 5.0,
            "n_workers": 1,
            "watersheds": "watershed_gura.shp",
            "sub_watersheds": "subwatersheds_gura.shp",
            "lulc": "land_use_gura.tif",
            "biophysical_table": "biophysical_table_gura.csv",
            "precipitation": "precipitation_gura.tif",
            "eto": "reference_ET_gura.tif",
            "depth_to_root_restricting_layer": "depth_to_root_restricting_layer_gura.tif",
            "plant_available_water_content": "plant_available_water_fraction_gura.tif",
            "invest_datastack": "annual_water_yield_gura.invs.json",
        },
        descriptor_version="annual_water_yield_gura_v1",
        executable=True,
    ),
    WaterYieldCaseDescriptor(
        case_id="annual_water_yield_blue_nile_fixture",
        display_name="Annual Water Yield - Blue Nile Fixture",
        aliases=("blue nile", "upper nile", "nile fixture"),
        intent_signals=("fixture blue nile", "metadata only case"),
        required_roles=(
            "watersheds",
            "lulc",
            "biophysical_table",
            "precipitation",
            "eto",
        ),
        provider_key="planning-pass1-invest-local",
        runtime_profile="docker-invest-real",
        sample_root="/sample-data/Annual_Water_Yield_Blue_Nile_Fixture",
        default_args={
            "results_suffix": "blue_nile_fixture",
            "seasonality_constant": 5.0,
            "n_workers": 1,
        },
        descriptor_version="annual_water_yield_blue_nile_fixture_v1",
        executable=False,
    ),
)

_CASE_BY_ID = {item.case_id: item for item in _WATER_YIELD_CASES}


def list_case_ids(known_case_ids: list[str] | None = None) -> list[str]:
    cases = list_cases(known_case_ids)
    return [case.case_id for case in cases]


def list_cases(known_case_ids: list[str] | None = None) -> list[WaterYieldCaseDescriptor]:
    if not known_case_ids:
        return list(_WATER_YIELD_CASES)
    filtered: list[WaterYieldCaseDescriptor] = []
    for case_id in known_case_ids:
        descriptor = _CASE_BY_ID.get(str(case_id).strip())
        if descriptor is not None:
            filtered.append(descriptor)
    return filtered or list(_WATER_YIELD_CASES)


def get_case(case_id: str | None) -> WaterYieldCaseDescriptor | None:
    if case_id is None:
        return None
    return _CASE_BY_ID.get(case_id.strip())


def match_cases(lower_query: str, known_case_ids: list[str] | None = None) -> list[WaterYieldCaseDescriptor]:
    candidates = []
    for descriptor in list_cases(known_case_ids):
        if descriptor.matches(lower_query):
            candidates.append(descriptor)
    return candidates
