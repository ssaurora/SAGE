package com.sage.backend.execution;

import com.sage.backend.execution.dto.CreateJobRequest;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.execution.dto.CancelJobRequest;
import com.sage.backend.execution.dto.CancelJobResponse;
import com.sage.backend.execution.dto.JobStatusResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JobRuntimeClient {

    private final RestClient restClient;

    public JobRuntimeClient(RestClient pass1RestClient) {
        this.restClient = pass1RestClient;
    }

    public CreateJobResponse createJob(CreateJobRequest request) {
        return restClient.post()
                .uri("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CreateJobResponse.class);
    }

    public JobStatusResponse getJob(String jobId) {
        return restClient.get()
                .uri("/jobs/{jobId}", jobId)
                .retrieve()
                .body(JobStatusResponse.class);
    }

    public CancelJobResponse cancelJob(String jobId, String reason) {
        CancelJobRequest request = new CancelJobRequest();
        request.setReason(reason);
        return restClient.post()
                .uri("/jobs/{jobId}/cancel", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CancelJobResponse.class);
    }
}
