# Water Yield Skill

This asset package defines the governed `water_yield` skill used by the Phase3 Week 2 pipeline.

The package is intentionally narrow:

- single skill: `water_yield`
- single analysis template: `water_yield_v1`
- governed annual water yield real-case execution
- schema-driven `args_draft` assembly via `parameter_schema.yaml`

The runtime authority remains outside this package. These assets define skill identity, parameter structure,
validation hints, repair guidance, and explanation constraints.
