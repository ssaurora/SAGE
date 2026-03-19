package com.sage.backend.planning;

import com.sage.backend.planning.dto.Pass2Request;
import com.sage.backend.planning.dto.Pass2Response;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Pass2Client {

    private final RestClient restClient;

    public Pass2Client(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public Pass2Response runPass2(Pass2Request request) {
        return restClient.post()
                .uri("/planning/pass2")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Pass2Response.class);
    }
}

