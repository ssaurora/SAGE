package com.sage.backend.scene;

import java.util.List;

final class DemoCaseProfileFactory {

    private DemoCaseProfileFactory() {
    }

    static DemoCaseProfile waterYieldGuraProfile() {
        return new DemoCaseProfile(
                "water_yield_gura_demo_v1",
                DemoLiveSimulationNarratives.DEMO_NARRATIVE_TYPE_WATER_YIELD,
                "Water Yield for Gura Subwatersheds",
                "Gura watershed",
                "Annual Water Yield Analysis",
                "water_yield",
                "water_yield_v1",
                "Identify which subwatersheds contribute the most annual water yield and summarize what the pattern implies for watershed management.",
                "Identify the subwatersheds with the highest annual water yield contribution in the Gura study area and explain the management implications.",
                List.of(
                        "watersheds",
                        "sub_watersheds",
                        "lulc",
                        "biophysical_table",
                        "precipitation",
                        "eto"
                ),
                "Watershed + subwatersheds",
                "Prioritize protection and monitoring in the high-yield catchments, while using lower-yield subwatersheds to flag areas where land-use pressure or vegetation change could reduce future supply stability.",
                List.of(
                        "Upper Gura catchments contribute the strongest annual water yield signal.",
                        "Subwatershed contribution is uneven, with a subset carrying disproportionate downstream supply significance.",
                        "Management attention should focus on protecting high-yield catchments and monitoring lower-yield areas under land-use pressure."
                ),
                "Annual water yield outputs are ready for the Gura watershed and subwatersheds, with the strongest contribution concentrated in a subset of upper catchments."
        );
    }

    record DemoCaseProfile(
            String demoCaseId,
            String demoNarrativeType,
            String caseDisplayName,
            String studyAreaName,
            String analysisKind,
            String capabilityKey,
            String selectedTemplate,
            String canonicalUserGoal,
            String canonicalFirstRequest,
            List<String> planInputRolesSummary,
            String spatialUnitsSummary,
            String managementInterpretationSeed,
            List<String> resultHighlightSeeds,
            String shortResultSummarySeed
    ) {
    }
}
