package com.sage.backend.cognition;

import com.sage.backend.cognition.dto.CognitionGoalRouteRequest;
import com.sage.backend.cognition.dto.CognitionGoalRouteResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CognitionGoalRouteClient {

    private final RestClient restClient;

    public CognitionGoalRouteClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public CognitionGoalRouteResponse route(CognitionGoalRouteRequest request) {
        return restClient.post()
                .uri("/cognition/goal-route")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CognitionGoalRouteResponse.class);
    }
}
