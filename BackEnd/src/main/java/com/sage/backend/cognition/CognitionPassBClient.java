package com.sage.backend.cognition;

import com.sage.backend.cognition.dto.CognitionPassBRequest;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CognitionPassBClient {

    private final RestClient restClient;

    public CognitionPassBClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public CognitionPassBResponse runPassB(CognitionPassBRequest request) {
        return restClient.post()
                .uri("/cognition/passb")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CognitionPassBResponse.class);
    }
}

