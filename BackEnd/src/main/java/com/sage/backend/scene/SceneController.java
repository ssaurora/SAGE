package com.sage.backend.scene;

import com.sage.backend.scene.dto.PostSceneSessionMessageRequest;
import com.sage.backend.scene.dto.PostSceneSessionMessageResponse;
import com.sage.backend.scene.dto.SceneDetailDTO;
import com.sage.backend.scene.dto.SceneListResponse;
import com.sage.backend.scene.dto.SessionMessagesResponseDTO;
import com.sage.backend.scene.dto.SessionProjectionDTO;
import com.sage.backend.security.CurrentUser;
import com.sage.backend.session.dto.UploadSessionAttachmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/scenes")
public class SceneController {

    private final SceneProjectionService sceneProjectionService;

    public SceneController(SceneProjectionService sceneProjectionService) {
        this.sceneProjectionService = sceneProjectionService;
    }

    @GetMapping
    public ResponseEntity<SceneListResponse> getScenes(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.getScenes(currentUser.userId(), status, query, limit));
    }

    @GetMapping("/{sceneId}")
    public ResponseEntity<SceneDetailDTO> getScene(
            @PathVariable String sceneId,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.getScene(currentUser.userId(), sceneId));
    }

    @GetMapping("/{sceneId}/session")
    public ResponseEntity<SessionProjectionDTO> getSceneSession(
            @PathVariable String sceneId,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.getSceneSession(currentUser.userId(), sceneId));
    }

    @GetMapping("/{sceneId}/session/messages")
    public ResponseEntity<SessionMessagesResponseDTO> getSceneSessionMessages(
            @PathVariable String sceneId,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.getSceneSessionMessages(currentUser.userId(), sceneId));
    }

    @PostMapping("/{sceneId}/session/messages")
    public ResponseEntity<PostSceneSessionMessageResponse> postSceneSessionMessage(
            @PathVariable String sceneId,
            @Valid @RequestBody PostSceneSessionMessageRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.postSceneSessionMessage(currentUser.userId(), sceneId, request));
    }

    @PostMapping("/{sceneId}/session/demo-live-simulation/reset")
    public ResponseEntity<SessionProjectionDTO> resetDemoLiveSimulationSession(
            @PathVariable String sceneId,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.resetDemoLiveSimulationSession(currentUser.userId(), sceneId));
    }

    @PostMapping(value = "/{sceneId}/session/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadSessionAttachmentResponse> uploadSceneSessionAttachment(
            @PathVariable String sceneId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "logical_slot", required = false) String logicalSlot,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(sceneProjectionService.uploadSceneSessionAttachment(currentUser.userId(), sceneId, file, logicalSlot));
    }
}
