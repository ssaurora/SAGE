package com.sage.backend.planning;

import com.fasterxml.jackson.databind.JsonNode;

public final class Pass1FactHelper {
    private static final String DEFAULT_CAPABILITY_KEY = "water_yield";
    private static final String DEFAULT_SELECTED_TEMPLATE = "water_yield_v1";

    private Pass1FactHelper() {
    }

    public static String resolveCapabilityKey(JsonNode goalParse, JsonNode skillRoute, JsonNode pass1Result) {
        String capabilityKey = DEFAULT_CAPABILITY_KEY;
        if (pass1Result != null && pass1Result.path("capability_key").isTextual()) {
            capabilityKey = pass1Result.path("capability_key").asText(DEFAULT_CAPABILITY_KEY);
        } else if (skillRoute != null && skillRoute.path("capability_key").isTextual()) {
            capabilityKey = skillRoute.path("capability_key").asText(DEFAULT_CAPABILITY_KEY);
        } else if (skillRoute != null && skillRoute.path("primary_skill").isTextual()) {
            capabilityKey = skillRoute.path("primary_skill").asText(DEFAULT_CAPABILITY_KEY);
        } else if (pass1Result != null && pass1Result.path("selected_template").isTextual()) {
            capabilityKey = pass1Result.path("selected_template").asText(DEFAULT_CAPABILITY_KEY);
        } else if (goalParse != null && goalParse.path("analysis_kind").isTextual()) {
            capabilityKey = goalParse.path("analysis_kind").asText(DEFAULT_CAPABILITY_KEY);
        } else if (goalParse != null && goalParse.path("goal_type").isTextual()) {
            capabilityKey = goalParse.path("goal_type").asText(DEFAULT_CAPABILITY_KEY);
        }
        return normalizeCapabilityKey(capabilityKey);
    }

    public static String normalizeCapabilityKey(String rawCapabilityKey) {
        if (rawCapabilityKey == null || rawCapabilityKey.isBlank()) {
            return DEFAULT_CAPABILITY_KEY;
        }

        return switch (rawCapabilityKey) {
            case "water_yield", "water_yield_v1", "water_yield_analysis",
                    "generic_analysis", "generic_analysis_request", "repairable_analysis_request" -> DEFAULT_CAPABILITY_KEY;
            default -> rawCapabilityKey;
        };
    }

    public static String resolveAnalysisTemplate(JsonNode pass1Result) {
        if (pass1Result != null && !pass1Result.isNull() && !pass1Result.isMissingNode()) {
            JsonNode stableDefaults = pass1Result.path("stable_defaults");
            if (stableDefaults.isObject()) {
                String fromDefaults = stableDefaults.path("analysis_template").asText("");
                if (!fromDefaults.isBlank()) {
                    return fromDefaults;
                }
            }
            String selectedTemplate = pass1Result.path("selected_template").asText("");
            if (!selectedTemplate.isBlank()) {
                return selectedTemplate;
            }
        }
        return DEFAULT_SELECTED_TEMPLATE;
    }

    public static String resolveTemplateVersion(JsonNode pass1Result) {
        if (pass1Result != null && !pass1Result.isNull() && !pass1Result.isMissingNode()) {
            String templateVersion = pass1Result.path("template_version").asText("");
            if (!templateVersion.isBlank()) {
                return templateVersion;
            }
        }
        return "";
    }

    public static double resolveStableDefaultDouble(JsonNode pass1Result, String key, double fallbackValue) {
        if (pass1Result != null && !pass1Result.isNull() && !pass1Result.isMissingNode()) {
            JsonNode stableDefaults = pass1Result.path("stable_defaults");
            if (stableDefaults.isObject()) {
                JsonNode value = stableDefaults.path(key);
                if (value.isNumber()) {
                    return value.asDouble();
                }
                if (value.isTextual()) {
                    try {
                        return Double.parseDouble(value.asText());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return fallbackValue;
    }

    public static String resolveRuntimeProfileHint(JsonNode pass1Result) {
        if (pass1Result != null && !pass1Result.isNull() && !pass1Result.isMissingNode()) {
            String runtimeProfileHint = pass1Result.path("capability_facts").path("runtime_profile_hint").asText("");
            if (!runtimeProfileHint.isBlank()) {
                return runtimeProfileHint;
            }
        }
        return "";
    }

    public static RoleArgMapping resolveRoleArgMapping(JsonNode pass1Result, String roleName) {
        if (pass1Result == null || roleName == null || roleName.isBlank()) {
            return null;
        }
        JsonNode roleMappings = pass1Result.path("role_arg_mappings");
        if (!roleMappings.isArray()) {
            return null;
        }
        for (JsonNode mapping : roleMappings) {
            if (!roleName.equals(mapping.path("role_name").asText(""))) {
                continue;
            }
            return new RoleArgMapping(
                    mapping.path("slot_arg_key").asText(""),
                    mapping.path("value_arg_key").asText(""),
                    mapping.has("default_value") ? mapping.get("default_value") : null
            );
        }
        return null;
    }

    public static String resolveExpectedSlotType(JsonNode pass1Result, String roleName) {
        if (pass1Result != null) {
            JsonNode validationHints = pass1Result.path("capability_facts").path("validation_hints");
            if (validationHints.isArray()) {
                for (JsonNode hint : validationHints) {
                    if (roleName.equals(hint.path("role_name").asText(""))) {
                        String expectedSlotType = hint.path("expected_slot_type").asText("");
                        if (!expectedSlotType.isBlank()) {
                            return expectedSlotType;
                        }
                    }
                }
            }

            JsonNode slots = pass1Result.path("slot_schema_view").path("slots");
            if (slots.isArray()) {
                for (JsonNode slot : slots) {
                    String boundRole = slot.path("bound_role").asText("");
                    String slotName = slot.path("slot_name").asText("");
                    if (roleName.equals(boundRole) || roleName.equals(slotName)) {
                        String type = slot.path("type").asText("");
                        if (!type.isBlank()) {
                            return type;
                        }
                    }
                }
            }
        }
        return "unknown";
    }

    public static RepairAction resolveRepairAction(JsonNode pass1Result, String roleName, String fallbackActionType) {
        JsonNode repairHints = pass1Result == null ? null : pass1Result.path("capability_facts").path("repair_hints");
        if (repairHints != null && repairHints.isArray()) {
            for (JsonNode hint : repairHints) {
                if (!roleName.equals(hint.path("role_name").asText(""))) {
                    continue;
                }
                String actionType = hint.path("action_type").asText(fallbackActionType);
                String actionKey = hint.path("action_key").asText(defaultActionKey(actionType, roleName));
                String actionLabel = hint.path("action_label").asText(defaultActionLabel(actionType, roleName));
                return new RepairAction(actionType, actionKey, actionLabel);
            }
        }

        return new RepairAction(
                fallbackActionType,
                defaultActionKey(fallbackActionType, roleName),
                defaultActionLabel(fallbackActionType, roleName)
        );
    }

    private static String defaultActionKey(String actionType, String roleOrParam) {
        if ("override".equals(actionType)) {
            return "repair_" + roleOrParam;
        }
        return "upload_" + roleOrParam;
    }

    private static String defaultActionLabel(String actionType, String roleOrParam) {
        if ("override".equals(actionType)) {
            return "Repair binding for " + roleOrParam;
        }
        return "Upload " + roleOrParam;
    }

    public record RoleArgMapping(String slotArgKey, String valueArgKey, JsonNode defaultValue) {
    }

    public record RepairAction(String actionType, String actionKey, String actionLabel) {
    }
}
