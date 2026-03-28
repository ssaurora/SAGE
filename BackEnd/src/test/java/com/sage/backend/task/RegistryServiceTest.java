package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.CapabilityRegistryMapper;
import com.sage.backend.mapper.ProviderRegistryMapper;
import com.sage.backend.model.CapabilityRegistryRecord;
import com.sage.backend.model.ProviderRegistryRecord;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvePrefersExplicitCapabilityKeyFromPass1() throws Exception {
        CapabilityRegistryMapper capabilityRegistryMapper = mock(CapabilityRegistryMapper.class);
        ProviderRegistryMapper providerRegistryMapper = mock(ProviderRegistryMapper.class);
        RegistryService service = new RegistryService(capabilityRegistryMapper, providerRegistryMapper);

        CapabilityRegistryRecord capability = new CapabilityRegistryRecord();
        capability.setCapabilityKey("water_yield");
        capability.setDefaultProviderKey("planning-pass1-local");
        when(capabilityRegistryMapper.findEnabledByCapabilityKey("water_yield")).thenReturn(capability);

        ProviderRegistryRecord provider = new ProviderRegistryRecord();
        provider.setProviderKey("planning-pass1-local");
        provider.setCapabilityKey("water_yield");
        provider.setRuntimeProfile("docker-local");
        when(providerRegistryMapper.findEnabledByProviderKey("planning-pass1-local")).thenReturn(provider);

        JsonNode goalParse = objectMapper.readTree("""
                {"analysis_kind": "generic_analysis"}
                """);
        JsonNode skillRoute = objectMapper.readTree("""
                {"primary_skill": "generic_analysis"}
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {"capability_key": "water_yield", "selected_template": "generic_analysis"}
                """);

        RegistryService.ProviderResolution resolution = service.resolve(goalParse, skillRoute, pass1Result);

        assertEquals("water_yield", resolution.capabilityKey());
        assertEquals("planning-pass1-local", resolution.providerKey());
        assertEquals("docker-local", resolution.runtimeProfile());
        verify(capabilityRegistryMapper).findEnabledByCapabilityKey("water_yield");
    }

    @Test
    void resolveFallsBackToCapabilityRuntimeProfileHintWhenProviderProfileBlank() throws Exception {
        CapabilityRegistryMapper capabilityRegistryMapper = mock(CapabilityRegistryMapper.class);
        ProviderRegistryMapper providerRegistryMapper = mock(ProviderRegistryMapper.class);
        RegistryService service = new RegistryService(capabilityRegistryMapper, providerRegistryMapper);

        CapabilityRegistryRecord capability = new CapabilityRegistryRecord();
        capability.setCapabilityKey("water_yield");
        capability.setDefaultProviderKey("planning-pass1-local");
        when(capabilityRegistryMapper.findEnabledByCapabilityKey("water_yield")).thenReturn(capability);

        ProviderRegistryRecord provider = new ProviderRegistryRecord();
        provider.setProviderKey("planning-pass1-local");
        provider.setCapabilityKey("water_yield");
        provider.setRuntimeProfile(" ");
        when(providerRegistryMapper.findEnabledByProviderKey("planning-pass1-local")).thenReturn(provider);

        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "capability_facts": {
                    "runtime_profile_hint": "docker-local"
                  }
                }
                """);

        RegistryService.ProviderResolution resolution = service.resolve(objectMapper.createObjectNode(), objectMapper.createObjectNode(), pass1Result);

        assertEquals("docker-local", resolution.runtimeProfile());
    }

    @Test
    void resolveRejectsProviderRuntimeProfileMismatchAgainstCapabilityHint() throws Exception {
        CapabilityRegistryMapper capabilityRegistryMapper = mock(CapabilityRegistryMapper.class);
        ProviderRegistryMapper providerRegistryMapper = mock(ProviderRegistryMapper.class);
        RegistryService service = new RegistryService(capabilityRegistryMapper, providerRegistryMapper);

        CapabilityRegistryRecord capability = new CapabilityRegistryRecord();
        capability.setCapabilityKey("water_yield");
        capability.setDefaultProviderKey("planning-pass1-local");
        when(capabilityRegistryMapper.findEnabledByCapabilityKey("water_yield")).thenReturn(capability);

        ProviderRegistryRecord provider = new ProviderRegistryRecord();
        provider.setProviderKey("planning-pass1-local");
        provider.setCapabilityKey("water_yield");
        provider.setRuntimeProfile("local-process");
        when(providerRegistryMapper.findEnabledByProviderKey("planning-pass1-local")).thenReturn(provider);

        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "capability_facts": {
                    "runtime_profile_hint": "docker-local"
                  }
                }
                """);

        assertThrows(
                ResponseStatusException.class,
                () -> service.resolve(objectMapper.createObjectNode(), objectMapper.createObjectNode(), pass1Result)
        );
    }

    @Test
    void resolveHonorsExplicitProviderPreferenceForRealCaseRoute() throws Exception {
        CapabilityRegistryMapper capabilityRegistryMapper = mock(CapabilityRegistryMapper.class);
        ProviderRegistryMapper providerRegistryMapper = mock(ProviderRegistryMapper.class);
        RegistryService service = new RegistryService(capabilityRegistryMapper, providerRegistryMapper);

        CapabilityRegistryRecord capability = new CapabilityRegistryRecord();
        capability.setCapabilityKey("water_yield");
        capability.setDefaultProviderKey("planning-pass1-local");
        when(capabilityRegistryMapper.findEnabledByCapabilityKey("water_yield")).thenReturn(capability);

        ProviderRegistryRecord provider = new ProviderRegistryRecord();
        provider.setProviderKey("planning-pass1-invest-local");
        provider.setCapabilityKey("water_yield");
        provider.setRuntimeProfile("docker-invest-real");
        when(providerRegistryMapper.findEnabledByProviderKey("planning-pass1-invest-local")).thenReturn(provider);

        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "provider_preference": "planning-pass1-invest-local",
                  "runtime_profile_preference": "docker-invest-real"
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "capability_facts": {
                    "runtime_profile_hint": "docker-local"
                  }
                }
                """);

        RegistryService.ProviderResolution resolution = service.resolve(objectMapper.createObjectNode(), skillRoute, pass1Result);

        assertEquals("planning-pass1-invest-local", resolution.providerKey());
        assertEquals("docker-invest-real", resolution.runtimeProfile());
        verify(providerRegistryMapper).findEnabledByProviderKey("planning-pass1-invest-local");
    }
}
