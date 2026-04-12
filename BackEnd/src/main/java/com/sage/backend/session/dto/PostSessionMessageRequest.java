package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public class PostSessionMessageRequest {
    @NotBlank
    private String content;

    @NotBlank
    @JsonProperty("client_request_id")
    private String clientRequestId;

    @JsonProperty("attachment_ids")
    private List<String> attachmentIds;

    @JsonProperty("slot_overrides")
    private Map<String, Object> slotOverrides;

    @JsonProperty("args_overrides")
    private Map<String, Object> argsOverrides;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public List<String> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<String> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public Map<String, Object> getSlotOverrides() {
        return slotOverrides;
    }

    public void setSlotOverrides(Map<String, Object> slotOverrides) {
        this.slotOverrides = slotOverrides;
    }

    public Map<String, Object> getArgsOverrides() {
        return argsOverrides;
    }

    public void setArgsOverrides(Map<String, Object> argsOverrides) {
        this.argsOverrides = argsOverrides;
    }
}
