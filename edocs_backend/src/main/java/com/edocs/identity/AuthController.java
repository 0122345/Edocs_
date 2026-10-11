package com.edocs.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.identity.IdentityDtos.ChangePasswordRequest;
import com.edocs.identity.IdentityDtos.ForgotPasswordRequest;
import com.edocs.identity.IdentityDtos.LoginRequest;
import com.edocs.identity.IdentityDtos.LoginResponse;
import com.edocs.identity.IdentityDtos.MfaVerifyRequest;
import com.edocs.identity.IdentityDtos.ResetPasswordRequest;
import com.edocs.identity.IdentityDtos.SessionDto;
import com.edocs.identity.IdentityDtos.SignupRequest;
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
    private final AccountService accounts;

    public AuthController(AuthService auth, AccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    // Creates a new workspace with the caller as its administrator; returns kind "verify" until the emailed code is entered.
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signup(@Valid @RequestBody SignupRequest req) {
        return accounts.signup(req.orgName(), req.name(), req.email(), req.password());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        accounts.changePassword(req.currentPassword(), req.newPassword());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        accounts.requestPasswordReset(req.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        accounts.resetPassword(req.email(), req.code(), req.newPassword());
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
