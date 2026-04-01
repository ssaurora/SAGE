package com.sage.backend.cognition;

import com.sage.backend.cognition.dto.CognitionFinalExplanationRequest;
import com.sage.backend.cognition.dto.CognitionFinalExplanationResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CognitionFinalExplanationClient {

    private final RestClient restClient;

    public CognitionFinalExplanationClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public CognitionFinalExplanationResponse generate(CognitionFinalExplanationRequest request) {
        return restClient.post()
                .uri("/cognition/final-explanation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CognitionFinalExplanationResponse.class);
    }
}
