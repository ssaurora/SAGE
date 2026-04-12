package com.sage.backend.session;

import com.sage.backend.security.CurrentUser;
import com.sage.backend.session.dto.AnalysisSessionResponse;
import com.sage.backend.session.dto.CreateSessionRequest;
import com.sage.backend.session.dto.CreateSessionResponse;
import com.sage.backend.session.dto.PostSessionMessageRequest;
import com.sage.backend.session.dto.SessionListResponse;
import com.sage.backend.session.dto.SessionMessagesResponse;
import com.sage.backend.session.dto.UploadSessionAttachmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(currentUser.userId(), request));
    }

    @GetMapping
    public ResponseEntity<SessionListResponse> listSessions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scene_id", required = false) String sceneId,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.listSessions(currentUser.userId(), status, sceneId, limit));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<AnalysisSessionResponse> getSession(@PathVariable String sessionId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.getSession(currentUser.userId(), sessionId));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<SessionMessagesResponse> getMessages(@PathVariable String sessionId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.getMessages(currentUser.userId(), sessionId));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<AnalysisSessionResponse> postMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody PostSessionMessageRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.postMessage(currentUser.userId(), sessionId, request));
    }

    @PostMapping(value = "/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadSessionAttachmentResponse> uploadAttachment(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "logical_slot", required = false) String logicalSlot,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                sessionService.uploadAttachment(currentUser.userId(), sessionId, file, logicalSlot)
        );
    }

    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSession(@PathVariable String sessionId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return sessionService.streamSession(currentUser.userId(), sessionId);
    }
}
