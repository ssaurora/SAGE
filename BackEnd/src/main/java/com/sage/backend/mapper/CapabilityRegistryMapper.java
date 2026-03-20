package com.sage.backend.mapper;

import com.sage.backend.model.CapabilityRegistryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CapabilityRegistryMapper {
    @Select("""
            SELECT capability_key, display_name, skill_name, enabled, default_provider_key, created_at
            FROM capability_registry
            WHERE capability_key = #{capabilityKey}
              AND enabled = TRUE
            LIMIT 1
            """)
    CapabilityRegistryRecord findEnabledByCapabilityKey(@Param("capabilityKey") String capabilityKey);
}
