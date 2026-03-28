package com.sage.backend.auth;

import com.sage.backend.auth.dto.LoginResponse;
import com.sage.backend.mapper.UserMapper;
import com.sage.backend.model.AppUser;
import com.sage.backend.security.CurrentUser;
import com.sage.backend.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(String username, String password) {
        AppUser user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername(), user.getRole());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(token);
        response.setTokenType("Bearer");
        response.setUser(new LoginResponse.UserView(String.valueOf(user.getId()), user.getUsername(), user.getRole()));
        return response;
    }

    public LoginResponse.UserView me(CurrentUser currentUser) {
        return new LoginResponse.UserView(String.valueOf(currentUser.userId()), currentUser.username(), currentUser.role());
    }
}
