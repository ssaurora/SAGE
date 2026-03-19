package com.sage.backend.validationgate;

import com.sage.backend.validationgate.dto.PrimitiveValidationRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ValidationClient {

    private final RestClient restClient;

    public ValidationClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public PrimitiveValidationResponse validatePrimitive(PrimitiveValidationRequest request) {
        return restClient.post()
                .uri("/validate/primitive")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PrimitiveValidationResponse.class);
    }
}

