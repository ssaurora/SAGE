package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RepairProposalClient {

    private final RestClient restClient;

    public RepairProposalClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public RepairProposalResponse generate(RepairProposalRequest request) {
        return restClient.post()
                .uri("/repair/proposal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RepairProposalResponse.class);
    }
}
