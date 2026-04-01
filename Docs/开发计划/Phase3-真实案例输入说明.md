# Phase3 真实案例输入说明

更新日期：2026-03-31

## 1. 适用范围

本说明固定对应当前 Phase3 前置验证阶段的单一 canonical case：

- `capability_key`: `water_yield`
- `case_id`: `annual_water_yield_gura`
- `provider_key`: `planning-pass1-invest-local`
- `runtime_profile`: `docker-invest-real`

本阶段不扩展多 case 编排，也不引入多 provider 竞争。

## 2. 样例目录约定

当前仓库内 canonical 样例目录为：

- [sample data/Annual_Water_Yield](/e:/paper_project/SAGE/sample%20data/Annual_Water_Yield)

compose / real-case E2E 默认挂载约定为：

- 宿主机样例根：`${SAGE_SAMPLE_DATA_ROOT_HOST}`
- 容器内样例根：`${SAGE_SAMPLE_DATA_ROOT:-/sample-data}`
- case 根目录：`/sample-data/Annual_Water_Yield`

真实 provider 构建入口固定为：

- [Service/planning-pass1/Dockerfile.invest-real](/e:/paper_project/SAGE/Service/planning-pass1/Dockerfile.invest-real)

推荐运行环境变量：

- `SAGE_SERVICE_DOCKERFILE=Dockerfile.invest-real`
- `SAGE_SERVICE_IMAGE=sage-pass1-invest-real:compose`
- `SAGE_SERVICE_RUNTIME_IMAGE=sage-pass1-invest-real:compose`
- `SAGE_INVEST_PIP_SPEC=natcap.invest`

## 3. 最小必需文件集

真实 `annual_water_yield_gura` 验证的最小必需输入如下。

必填输入：

- `watersheds` -> `watershed_gura.shp`
- `lulc` -> `land_use_gura.tif`
- `biophysical_table` -> `biophysical_table_gura.csv`
- `precipitation` -> `precipitation_gura.tif`
- `eto` -> `reference_ET_gura.tif`

可选输入：

- `depth_to_root_restricting_layer` -> `depth_to_root_restricting_layer_gura.tif`
- `plant_available_water_content` -> `plant_available_water_fraction_gura.tif`

当前 case 还包含以下补充文件：

- `subwatersheds_gura.shp`
- `annual_water_yield_gura.invs.json`
- shapefile 相关 `dbf / shx / prj / sbn / sbx / xml`

## 4. 当前 contract 对应关系

planning/capability 层当前已将以下角色视为真实 contract 的输入角色：

- 必填：`watersheds`、`lulc`、`biophysical_table`、`precipitation`、`eto`
- 可选：`depth_to_root_restricting_layer`、`plant_available_water_content`

control layer 当前组装到 real-case runtime 的关键参数包括：

- `analysis_template=water_yield_v1`
- `case_id=annual_water_yield_gura`
- `case_profile_version=water_yield_case_contract_v1`
- `contract_mode=invest_real_case_v1`
- `runtime_mode=invest_real_runner`
- `seasonality_constant=5.0`
- `workspace_dir`
- `results_suffix`
- `*_path` 形式的真实输入路径

## 5. 验证与 repair 约定

当前 WAITING_USER repair 视图必须围绕真实输入角色展开，而不是泛化 demo 参数。

当前已明确支持的 upload repair action 包括：

- `upload_watersheds`
- `upload_lulc`
- `upload_biophysical_table`
- `upload_precipitation`
- `upload_eto`
- `upload_depth_to_root_restricting_layer`
- `upload_plant_available_water_content`

真实 repair/resume 验收脚本使用的缺输入场景为：

- query: `run a real case invest annual water yield analysis for gura missing precipitation`

## 6. 当前脚本入口

真实案例多场景验收脚本：

- [scripts/phase3-realcase-e2e.ps1](/e:/paper_project/SAGE/scripts/phase3-realcase-e2e.ps1)

统一 gate 入口：

- [scripts/phase1-gate.ps1](/e:/paper_project/SAGE/scripts/phase1-gate.ps1)

推荐执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase3-realcase-e2e.ps1 -Scenario All
```
