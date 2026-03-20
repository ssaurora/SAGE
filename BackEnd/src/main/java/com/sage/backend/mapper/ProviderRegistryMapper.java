package com.sage.backend.mapper;

import com.sage.backend.model.ProviderRegistryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProviderRegistryMapper {
    @Select("""
            SELECT provider_key, capability_key, provider_type, base_url, runtime_profile, enabled, priority, created_at
            FROM provider_registry
            WHERE provider_key = #{providerKey}
              AND enabled = TRUE
            LIMIT 1
            """)
    ProviderRegistryRecord findEnabledByProviderKey(@Param("providerKey") String providerKey);
}
