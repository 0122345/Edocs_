package com.edocs.identity;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record UserDto(String id, String name, String email, Role role, String initials, boolean mfaEnabled, KycStatus kycStatus,
            boolean active, String joinedAt) {

        public static UserDto of(Membership m) {
            User u = m.getUser();
            return new UserDto(u.getId().toString(), u.getFullName(), u.getEmail(), u.getRole(), u.initials(), u.isMfaEnabled(),
                    u.getKycStatus(), m.isActive(), m.getJoinedAt().toString());
        }
    }

    public record SessionDto(String token, UserDto user) {
    }

    // kind = "session" (signed in) or "mfa" (a code was sent; call /auth/mfa/verify).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String kind, SessionDto session, String challengeId, String email) {

        public static LoginResponse session(SessionDto s) {
            return new LoginResponse("session", s, null, null);
        }

        public static LoginResponse mfa(String challengeId, String email) {
            return new LoginResponse("mfa", null, challengeId, email);
        }
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record MfaVerifyRequest(@NotBlank String challengeId, @NotBlank @Size(max = 12) String code) {
    }

    public record InviteMemberRequest(@NotBlank @Email @Size(max = 254) String email, @Size(max = 120) String name, @NotNull Role role) {
    }

    public record UpdateMemberRequest(Role role, Boolean active, Boolean mfaEnabled) {
    }

    public record SettingsDto(
            @NotBlank @Size(max = 160) String orgName,
            @NotBlank @Email String adminEmail,
            @NotNull SignatureLevel signatureLevel,
            @Min(1) @Max(30) int retentionYears,
            boolean require2fa) {
    }
}
