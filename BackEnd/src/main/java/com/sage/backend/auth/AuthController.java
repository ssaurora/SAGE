package com.sage.backend.auth;

import com.sage.backend.auth.dto.LoginRequest;
import com.sage.backend.auth.dto.LoginResponse;
import com.sage.backend.auth.dto.MeResponse;
import com.sage.backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        LoginResponse.UserView user = authService.me(currentUser);
        return ResponseEntity.ok(new MeResponse(user.getUserId(), user.getUsername()));
    }
}

