INSERT INTO provider_registry(provider_key, capability_key, provider_type, base_url, runtime_profile, enabled, priority)
VALUES ('planning-pass1-invest-local', 'water_yield', 'HTTP', 'planning-pass1', 'docker-invest-real', TRUE, 90)
ON CONFLICT (provider_key) DO NOTHING;
