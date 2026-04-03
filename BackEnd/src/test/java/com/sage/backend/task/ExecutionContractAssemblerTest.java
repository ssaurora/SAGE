package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionContractAssemblerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assembleCopiesStableDefaultsFromPass1WithoutHardcodedFallbacks() throws Exception {
        ExecutionContractAssembler assembler = new ExecutionContractAssembler(objectMapper);
        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "execution_mode": "real_case_validation"
                }
                """);
        JsonNode pass1 = objectMapper.readTree("""
                {
                  "selected_template": "water_yield_v1",
                  "stable_defaults": {
                    "seasonality_constant": 5.0,
                    "root_depth_factor": 0.8,
                    "pawc_factor": 0.85
                  }
                }
                """);
        ObjectNode passB = (ObjectNode) objectMapper.readTree("""
                {
                  "binding_status": "resolved",
                  "slot_bindings": [
                    {"role_name": "watersheds", "slot_name": "watersheds"}
                  ],
                  "user_semantic_args": {
                    "case_id": "annual_water_yield_gura"
                  },
                  "inferred_semantic_args": {},
                  "args_draft": {}
                }
                """);

        ExecutionContractAssembler.AssemblyResult result = assembler.assemble("task_week4", skillRoute, pass1, passB);

        assertFalse(result.assemblyBlocked());
        ObjectNode argsDraft = (ObjectNode) passB.get("args_draft");
        assertEquals(5.0, argsDraft.path("seasonality_constant").asDouble());
        assertEquals(0.8, argsDraft.path("root_depth_factor").asDouble());
        assertEquals(0.85, argsDraft.path("pawc_factor").asDouble());
    }

    @Test
    void assembleLeavesDomainDefaultsAbsentWhenPass1DoesNotProvideThem() throws Exception {
        ExecutionContractAssembler assembler = new ExecutionContractAssembler(objectMapper);
        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "execution_mode": "real_case_validation"
                }
                """);
        JsonNode pass1 = objectMapper.readTree("""
                {
                  "selected_template": "water_yield_v1",
                  "stable_defaults": {}
                }
                """);
        ObjectNode passB = (ObjectNode) objectMapper.readTree("""
                {
                  "binding_status": "resolved",
                  "slot_bindings": [
                    {"role_name": "watersheds", "slot_name": "watersheds"}
                  ],
                  "user_semantic_args": {
                    "case_id": "annual_water_yield_gura"
                  },
                  "inferred_semantic_args": {},
                  "args_draft": {}
                }
                """);

        assembler.assemble("task_week4", skillRoute, pass1, passB);

        ObjectNode argsDraft = (ObjectNode) passB.get("args_draft");
        assertFalse(argsDraft.has("seasonality_constant"));
        assertFalse(argsDraft.has("root_depth_factor"));
        assertFalse(argsDraft.has("pawc_factor"));
    }
}
