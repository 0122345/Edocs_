package com.edocs.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.identity.IdentityDtos.LoginRequest;
import com.edocs.identity.IdentityDtos.LoginResponse;
import com.edocs.identity.IdentityDtos.MfaVerifyRequest;
import com.edocs.identity.IdentityDtos.SessionDto;
import com.edocs.identity.IdentityDtos.UserDto;
import com.edocs.security.AuthUserAuthentication;
import com.edocs.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @PostMapping("/mfa/verify")
    public SessionDto verifyMfa(@Valid @RequestBody MfaVerifyRequest req) {
        return auth.verifyMfa(req.challengeId(), req.code());
    }

    @GetMapping("/me")
    public UserDto me() {
        return auth.me(CurrentUser.get());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(AuthUserAuthentication authentication) {
        auth.logout(authentication.getJwt().getId());
    }
}
