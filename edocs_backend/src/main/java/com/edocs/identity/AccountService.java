package com.edocs.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.security.LoginRateLimiter;
import com.edocs.seed.WorkspaceProvisioner;
import com.edocs.signing.OtpChallenge;
import com.edocs.signing.OtpChallengeRepository;
import com.edocs.signing.OtpService;

// Self-service accounts: sign-up creates a workspace owned by its first admin, plus password change and reset by emailed code.
@Service
public class AccountService {

    private static final String BAD_RESET_CODE = "That code is wrong or has expired. Request a new one and try again.";

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final OtpService otps;
    private final OtpChallengeRepository challenges;
    private final NotificationService notifications;
    private final AuditService audit;
    private final LoginRateLimiter limiter;
    private final WorkspaceProvisioner provisioner;
    private final EdocsProperties props;

    public AccountService(UserRepository users, MembershipRepository memberships, OrganizationRepository organizations, PasswordEncoder encoder,
            AuthService auth, OtpService otps, OtpChallengeRepository challenges, NotificationService notifications, AuditService audit,
            LoginRateLimiter limiter, WorkspaceProvisioner provisioner, EdocsProperties props) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.encoder = encoder;
        this.auth = auth;
        this.otps = otps;
        this.challenges = challenges;
        this.notifications = notifications;
        this.audit = audit;
        this.limiter = limiter;
        this.provisioner = provisioner;
        this.props = props;
    }

    // The account stays unverified, and cannot sign in, until the emailed code is entered.
    @Transactional
    public LoginResponse signup(String orgName, String name, String email, String password) {
        limiter.check(ClientInfo.ip());
        String normalized = email.trim().toLowerCase();
        checkPolicy(password, normalized);
        User existing = users.findByEmail(normalized).orElse(null);
        if (existing != null) {
            if (existing.isEmailVerified()) {
                throw ApiException.conflict("An account already exists for " + normalized + ". Sign in, or use “Forgot password”.");
            }
            discardPendingSignup(existing);
        }
        Organization org = organizations.save(new Organization(orgName.trim(), null, normalized));
        User user = new User(name.trim(), normalized, Role.ADMIN);
        user.setPasswordHash(encoder.encode(password));
        user.setEmailVerified(false);
        users.save(user);
        Membership owner = memberships.save(new Membership(user, org, LocalDate.now(ZoneOffset.UTC)));
        provisioner.provision(org, owner);
        audit.append(new Actor(org.getId().toString(), user.getId().toString(), normalized, ClientInfo.ip()), AuditKind.CREATE,
                "Workspace created by " + normalized, null, "Workspace", AuditStatus.VERIFIED, Instant.now());
        return auth.sendVerification(user, owner);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AuthUser me = CurrentUser.get();
        User user = users.findById(me.userId()).orElseThrow(() -> ApiException.unauthorized("Your session has ended. Sign in again."));
        if (user.getPasswordHash() == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unprocessable("Your current password is incorrect.");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.unprocessable("Choose a password you haven't used for this account.");
        }
        checkPolicy(newPassword, user.getEmail());
        user.setPasswordHash(encoder.encode(newPassword));
        audit.record(AuditKind.KEY_ROTATION, "Password changed: " + user.getEmail(), null);
    }

    // Always succeeds silently so the response never reveals whether an account exists.
    @Transactional
    public void requestPasswordReset(String email) {
        limiter.check(ClientInfo.ip());
        users.findByEmail(email.trim().toLowerCase()).ifPresent(user -> memberships.findByUserId(user.getId()).stream()
                .filter(Membership::isActive).findFirst().ifPresent(m -> {
                    OtpService.Issued issued = otps.issue(user.getId(), null, OtpChallenge.Purpose.RESET);
                    notifications.send(m.getOrganization().getId(), Recipient.user(user.getId(), user.getEmail()), NotificationType.OTP, Channel.EMAIL,
                            "Reset your password", "A password reset code was emailed to you.", null,
                            "Your Edocs password reset code is " + issued.code() + ". It expires in " + props.otp().ttl().toMinutes()
                                    + " minutes. If you didn't ask for this, ignore this email.");
                }));
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        limiter.check(ClientInfo.ip());
        User user = users.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> ApiException.unprocessable(BAD_RESET_CODE));
        OtpChallenge challenge = challenges.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), OtpChallenge.Purpose.RESET)
                .orElseThrow(() -> ApiException.unprocessable(BAD_RESET_CODE));
        checkPolicy(newPassword, user.getEmail());
        otps.verify(challenge.getId(), code, null);
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // The code reached this inbox, so the address is proven even for a pending sign-up.
        user.setEmailVerified(true);
        Membership m = auth.activeMembership(user);
        audit.append(new Actor(m.getOrganization().getId().toString(), user.getId().toString(), user.getEmail(), ClientInfo.ip()), AuditKind.KEY_ROTATION,
                "Password reset by emailed code", null, "Workspace", AuditStatus.VERIFIED, Instant.now());
    }

    static void checkPolicy(String password, String email) {
        boolean letters = password.chars().anyMatch(Character::isLetter);
        boolean digits = password.chars().anyMatch(Character::isDigit);
        if (password.length() < 10 || !letters || !digits) {
            throw ApiException.unprocessable("Use at least 10 characters, including letters and numbers.");
        }
        String local = email.substring(0, Math.max(0, email.indexOf('@'))).toLowerCase();
        if (password.equalsIgnoreCase(email) || (local.length() >= 4 && password.toLowerCase().contains(local))) {
            throw ApiException.unprocessable("Your password must not contain your email address.");
        }
    }

    // A sign-up nobody confirmed can be replaced: its workspace has no other members and no data of its own.
    private void discardPendingSignup(User pending) {
        for (Membership m : memberships.findByUserId(pending.getId())) {
            Organization org = m.getOrganization();
            boolean onlyMember = memberships.findByOrgId(org.getId()).size() == 1;
            memberships.delete(m);
            memberships.flush();
            if (onlyMember) {
                organizations.delete(org);
            }
        }
        users.delete(pending);
        users.flush();
    }
}
