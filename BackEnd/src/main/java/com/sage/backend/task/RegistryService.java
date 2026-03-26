package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.mapper.CapabilityRegistryMapper;
import com.sage.backend.mapper.ProviderRegistryMapper;
import com.sage.backend.model.CapabilityRegistryRecord;
import com.sage.backend.model.ProviderRegistryRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistryService {
    private final CapabilityRegistryMapper capabilityRegistryMapper;
    private final ProviderRegistryMapper providerRegistryMapper;

    public RegistryService(CapabilityRegistryMapper capabilityRegistryMapper, ProviderRegistryMapper providerRegistryMapper) {
        this.capabilityRegistryMapper = capabilityRegistryMapper;
        this.providerRegistryMapper = providerRegistryMapper;
    }

    public ProviderResolution resolve(JsonNode goalParse, JsonNode skillRoute, JsonNode pass1Result) {
        String capabilityKey = "water_yield";
        if (pass1Result != null && pass1Result.path("capability_key").isTextual()) {
            capabilityKey = pass1Result.path("capability_key").asText("water_yield");
        } else if (skillRoute != null && skillRoute.path("capability_key").isTextual()) {
            capabilityKey = skillRoute.path("capability_key").asText("water_yield");
        } else if (skillRoute != null && skillRoute.path("primary_skill").isTextual()) {
            capabilityKey = skillRoute.path("primary_skill").asText("water_yield");
        } else if (pass1Result != null && pass1Result.path("selected_template").isTextual()) {
            capabilityKey = pass1Result.path("selected_template").asText("water_yield");
        } else if (goalParse != null && goalParse.path("analysis_kind").isTextual()) {
            capabilityKey = goalParse.path("analysis_kind").asText("water_yield");
        } else if (goalParse != null && goalParse.path("goal_type").isTextual()) {
            capabilityKey = goalParse.path("goal_type").asText("water_yield");
        }
        capabilityKey = normalizeCapabilityKey(capabilityKey);

        CapabilityRegistryRecord capability = capabilityRegistryMapper.findEnabledByCapabilityKey(capabilityKey);
        if (capability == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Capability is not registered: " + capabilityKey);
        }
        ProviderRegistryRecord provider = providerRegistryMapper.findEnabledByProviderKey(capability.getDefaultProviderKey());
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider is not registered: " + capability.getDefaultProviderKey());
        }
        return new ProviderResolution(capability.getCapabilityKey(), provider.getProviderKey(), provider.getRuntimeProfile());
    }

    private String normalizeCapabilityKey(String rawCapabilityKey) {
        if (rawCapabilityKey == null || rawCapabilityKey.isBlank()) {
            return "water_yield";
        }

        return switch (rawCapabilityKey) {
            case "water_yield", "water_yield_v1", "water_yield_analysis",
                    "generic_analysis", "generic_analysis_request", "repairable_analysis_request" -> "water_yield";
            default -> rawCapabilityKey;
        };
    }

    public record ProviderResolution(String capabilityKey, String providerKey, String runtimeProfile) {
    }
}
