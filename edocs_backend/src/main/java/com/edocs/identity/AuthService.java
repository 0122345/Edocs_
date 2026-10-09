package com.edocs.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.audit.AuditService.Actor;
import com.edocs.audit.AuditStatus;
import com.edocs.common.ApiException;
import com.edocs.common.ClientInfo;
import com.edocs.config.EdocsProperties;
import com.edocs.identity.IdentityDtos.LoginResponse;
import com.edocs.identity.IdentityDtos.SessionDto;
import com.edocs.identity.IdentityDtos.UserDto;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.security.AuthUser;
import com.edocs.security.JwtService;
import com.edocs.security.LoginRateLimiter;
import com.edocs.security.TokenDenylist;
import com.edocs.signing.OtpChallenge;
import com.edocs.signing.OtpChallengeRepository;
import com.edocs.signing.OtpService;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Email or password is incorrect.";

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TokenDenylist denylist;
    private final OtpService otps;
    private final OtpChallengeRepository challenges;
    private final NotificationService notifications;
    private final AuditService audit;
    private final LoginRateLimiter limiter;
    private final EdocsProperties props;
    // Compared against when the email is unknown so response time does not reveal which accounts exist.
    private final String dummyHash;

    public AuthService(UserRepository users, MembershipRepository memberships, PasswordEncoder encoder, JwtService jwt, TokenDenylist denylist,
            OtpService otps, OtpChallengeRepository challenges, NotificationService notifications, AuditService audit, LoginRateLimiter limiter,
            EdocsProperties props) {
        this.users = users;
        this.memberships = memberships;
        this.encoder = encoder;
        this.jwt = jwt;
        this.denylist = denylist;
        this.otps = otps;
        this.challenges = challenges;
        this.notifications = notifications;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    // Failed-attempt counters must survive the 401, hence no rollback for ApiException.
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String email, String password) {
        limiter.check(ClientInfo.ip());
        Instant now = Instant.now();
        User user = users.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            encoder.matches(password, dummyHash);
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes());
            throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            registerFailure(user, now);
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        Membership m = activeMembership(user);
        if (user.isMfaEnabled()) {
            OtpService.Issued issued = otps.issue(user.getId(), null, OtpChallenge.Purpose.MFA);
            notifications.send(m.getOrganization().getId(), Recipient.user(user.getId(), user.getEmail()), NotificationType.OTP, Channel.EMAIL,
                    "Your sign-in code", "A sign-in code was emailed to you.", null,
                    "Your Edocs sign-in code is " + issued.code() + ". It expires in " + props.otp().ttl().toMinutes() + " minutes.");
            return LoginResponse.mfa(issued.challenge().getId().toString(), user.getEmail());
        }
        return LoginResponse.session(startSession(user, m, "password"));
    }

    @Transactional
    public SessionDto verifyMfa(String challengeId, String code) {
        UUID id;
        try {
            id = UUID.fromString(challengeId);
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("That code is incorrect. Check your authenticator app and try again.");
        }
        OtpChallenge challenge = challenges.findById(id)
                .filter(c -> c.getPurpose() == OtpChallenge.Purpose.MFA)
                .orElseThrow(() -> ApiException.unauthorized("That sign-in attempt has expired. Sign in again."));
        otps.verify(challenge.getId(), code, props.security().demoMfaCode());
        User user = users.findById(challenge.getUserId()).orElseThrow();
        return startSession(user, activeMembership(user), "password+mfa");
    }

    // Called after a successful OAuth2 authorization-code login; only invited members may sign in.
    @Transactional
    public SessionDto loginWithProvider(String email, boolean idpMfa) {
        if (email == null) {
            throw ApiException.unauthorized("The identity provider did not share a verified email address.");
        }
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.forbidden(email + " is not a member of any Edocs workspace. Ask an administrator for an invite."));
        // SSO must not bypass MFA: the provider has to assert it, otherwise use password + code.
        if (user.isMfaEnabled() && !idpMfa) {
            throw ApiException.forbidden("Your account requires MFA. Sign in with your password and code.");
        }
        return startSession(user, activeMembership(user), "oauth2");
    }

    public void logout(String jti) {
        denylist.revoke(jti);
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser me) {
        return memberships.findByOrgIdAndUserId(me.orgId(), me.userId()).map(UserDto::of)
                .orElseThrow(() -> ApiException.unauthorized("Your session has ended. Sign in again."));
    }

    private SessionDto startSession(User user, Membership m, String method) {
        user.setLastLogin(Instant.now());
        users.save(user);
        AuthUser principal = new AuthUser(user.getId(), m.getOrganization().getId(), user.getEmail(), user.getFullName(), user.getRole());
        audit.append(new Actor(principal.orgId().toString(), user.getId().toString(), user.getEmail(), ClientInfo.ip()),
                AuditKind.VIEW, "Signed in (" + method + ")", null, "Workspace", AuditStatus.VERIFIED, Instant.now());
        return new SessionDto(jwt.issue(principal), UserDto.of(m));
    }

    private Membership activeMembership(User user) {
        return memberships.findByUserId(user.getId()).stream().filter(Membership::isActive).findFirst()
                .orElseThrow(() -> ApiException.forbidden("This account is deactivated. Ask an administrator to reactivate it."));
    }

    private void registerFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        if (failures >= props.security().maxFailedLogins()) {
            user.setLockedUntil(now.plus(props.security().lockout()));
            failures = 0;
        }
        user.setFailedLoginAttempts(failures);
        users.save(user);
    }
}
