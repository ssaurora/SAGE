package com.sage.backend.validationgate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PrimitiveValidationResponse {
    @JsonProperty("is_valid")
    private Boolean isValid;

    @JsonProperty("missing_roles")
    private List<String> missingRoles;

    @JsonProperty("missing_params")
    private List<String> missingParams;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("invalid_bindings")
    private List<String> invalidBindings;

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public List<String> getMissingRoles() {
        return missingRoles;
    }

    public void setMissingRoles(List<String> missingRoles) {
        this.missingRoles = missingRoles;
    }

    public List<String> getMissingParams() {
        return missingParams;
    }

    public void setMissingParams(List<String> missingParams) {
        this.missingParams = missingParams;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public List<String> getInvalidBindings() {
        return invalidBindings;
    }

    public void setInvalidBindings(List<String> invalidBindings) {
        this.invalidBindings = invalidBindings;
    }
}
