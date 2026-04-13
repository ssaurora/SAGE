package com.sage.backend.scene;

import com.sage.backend.scene.dto.SceneDetailDTO;
import com.sage.backend.scene.dto.SceneListResponse;
import com.sage.backend.scene.dto.SessionProjectionDTO;
import com.sage.backend.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
