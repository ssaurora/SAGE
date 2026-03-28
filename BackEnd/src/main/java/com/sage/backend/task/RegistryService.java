package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.mapper.CapabilityRegistryMapper;
import com.sage.backend.mapper.ProviderRegistryMapper;
import com.sage.backend.model.CapabilityRegistryRecord;
import com.sage.backend.model.ProviderRegistryRecord;
import com.sage.backend.planning.Pass1FactHelper;
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
        String capabilityKey = Pass1FactHelper.resolveCapabilityKey(goalParse, skillRoute, pass1Result);
        String runtimeProfileHint = Pass1FactHelper.resolveRuntimeProfileHint(pass1Result);
        String providerPreference = resolveTextPreference(skillRoute, "provider_preference");
        if (providerPreference.isBlank()) {
            providerPreference = resolveTextPreference(goalParse, "provider_preference");
        }
        String runtimeProfilePreference = resolveTextPreference(skillRoute, "runtime_profile_preference");
        if (runtimeProfilePreference.isBlank()) {
            runtimeProfilePreference = resolveTextPreference(goalParse, "runtime_profile_preference");
        }

        CapabilityRegistryRecord capability = capabilityRegistryMapper.findEnabledByCapabilityKey(capabilityKey);
        if (capability == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Capability is not registered: " + capabilityKey);
        }
        String providerKey = providerPreference.isBlank() ? capability.getDefaultProviderKey() : providerPreference;
        ProviderRegistryRecord provider = providerRegistryMapper.findEnabledByProviderKey(providerKey);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider is not registered: " + providerKey);
        }
        if (!capabilityKey.equals(provider.getCapabilityKey())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Provider does not match capability: " + provider.getProviderKey()
            );
        }
        String providerRuntimeProfile = provider.getRuntimeProfile() == null ? "" : provider.getRuntimeProfile().trim();
        String expectedRuntimeProfile = runtimeProfilePreference.isBlank() ? runtimeProfileHint : runtimeProfilePreference;
        if (!expectedRuntimeProfile.isBlank()
                && !providerRuntimeProfile.isBlank()
                && !expectedRuntimeProfile.equals(providerRuntimeProfile)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Provider runtime profile does not satisfy capability hint: " + capabilityKey
            );
        }
        String resolvedRuntimeProfile = providerRuntimeProfile.isBlank() ? expectedRuntimeProfile : providerRuntimeProfile;
        return new ProviderResolution(capability.getCapabilityKey(), provider.getProviderKey(), resolvedRuntimeProfile);
    }

    private String resolveTextPreference(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        JsonNode value = node.path(fieldName);
        if (!value.isTextual()) {
            return "";
        }
        return value.asText("").trim();
    }

    public record ProviderResolution(String capabilityKey, String providerKey, String runtimeProfile) {
    }
}
