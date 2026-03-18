package com.sage.backend.planning;

import com.sage.backend.planning.dto.Pass1Request;
import com.sage.backend.planning.dto.Pass1Response;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Pass1Client {

    private final RestClient restClient;

    public Pass1Client(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public Pass1Response runPass1(Pass1Request request) {
        return restClient.post()
                .uri("/planning/pass1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Pass1Response.class);
    }
}

