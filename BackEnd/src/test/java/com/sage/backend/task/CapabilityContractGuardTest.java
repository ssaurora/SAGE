package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityContractGuardTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsExpectedValidationResumeAndSubmitContracts() throws Exception {
        JsonNode pass1Node = objectMapper.readTree(samplePass1Json());

        assertDoesNotThrow(() -> CapabilityContractGuard.requireResumeAckContract(pass1Node));
        assertDoesNotThrow(() -> CapabilityContractGuard.requireValidationContracts(pass1Node));
        assertDoesNotThrow(() -> CapabilityContractGuard.requireSubmitJobContract(pass1Node));
        assertDoesNotThrow(() -> CapabilityContractGuard.requireQueryJobStatusContract(pass1Node));
        assertDoesNotThrow(() -> CapabilityContractGuard.requireCollectResultBundleContract(pass1Node));
    }

    @Test
    void failsWhenRequiredContractMissing() throws Exception {
        JsonNode pass1Node = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contracts": {
                      "validate_bindings": {
                        "input_schema": "slot_bindings_validation_v1",
                        "output_schema": "binding_validation_summary_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      }
                    }
                  }
                }
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CapabilityContractGuard.requireSubmitJobContract(pass1Node)
        );

        assertEquals("CAPABILITY_CONTRACT_UNAVAILABLE: submit_job", exception.getMessage());
    }

    @Test
    void failsWhenCallerScopeOrSideEffectMismatch() throws Exception {
        JsonNode pass1Node = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contracts": {
                      "checkpoint_resume_ack": {
                        "input_schema": "checkpoint_resume_request_v1",
                        "output_schema": "checkpoint_resume_ack_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      }
                    }
                  }
                }
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CapabilityContractGuard.requireResumeAckContract(pass1Node)
        );

        assertEquals("CAPABILITY_CONTRACT_MISMATCH: checkpoint_resume_ack caller_scope", exception.getMessage());
    }

    private String samplePass1Json() {
        return """
                {
                  "capability_facts": {
                    "contracts": {
                      "validate_bindings": {
                        "input_schema": "slot_bindings_validation_v1",
                        "output_schema": "binding_validation_summary_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      },
                      "validate_args": {
                        "input_schema": "args_draft_validation_v1",
                        "output_schema": "arg_validation_summary_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      },
                      "checkpoint_resume_ack": {
                        "input_schema": "checkpoint_resume_request_v1",
                        "output_schema": "checkpoint_resume_ack_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "workflow_checkpoint"
                      },
                      "submit_job": {
                        "input_schema": "create_job_request_v1",
                        "output_schema": "create_job_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "runtime_submission"
                      },
                      "query_job_status": {
                        "input_schema": "job_status_request_v1",
                        "output_schema": "job_status_response_v1",
                        "caller_scope": "control_or_presentation",
                        "side_effect_level": "read_only"
                      },
                      "collect_result_bundle": {
                        "input_schema": "result_bundle_collection_request_v1",
                        "output_schema": "result_bundle_collection_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "artifact_collection"
                      }
                    }
                  }
                }
                """;
    }
}
