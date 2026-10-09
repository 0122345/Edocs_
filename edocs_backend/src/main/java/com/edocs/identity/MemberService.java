package com.edocs.identity;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.common.ApiException;
import com.edocs.config.EdocsProperties;
import com.edocs.identity.IdentityDtos.UpdateMemberRequest;
import com.edocs.identity.IdentityDtos.UserDto;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.security.Permission;
import com.edocs.security.PrincipalService;

@Service
public class MemberService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#%";

    private final MembershipRepository memberships;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PrincipalService principals;
    private final EdocsProperties props;
    private final SecureRandom random = new SecureRandom();

    public MemberService(MembershipRepository memberships, UserRepository users, OrganizationRepository organizations, PasswordEncoder encoder,
            NotificationService notifications, AuditService audit, PrincipalService principals, EdocsProperties props) {
        this.memberships = memberships;
        this.users = users;
        this.organizations = organizations;
        this.encoder = encoder;
        this.notifications = notifications;
        this.audit = audit;
        this.principals = principals;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return memberships.findByOrgId(CurrentUser.get().orgId()).stream().map(UserDto::of).toList();
    }

    // Creates the account with a one-time temporary password emailed through RabbitMQ (SSO also works for the invited email).
    @Transactional
    public UserDto invite(String email, String name, Role role) {
        AuthUser me = CurrentUser.get();
        String normalized = email.trim().toLowerCase();
        if (memberships.findByOrgIdAndEmail(me.orgId(), normalized).isPresent()) {
            throw ApiException.conflict(normalized + " is already a member.");
        }
        String tempPassword = null;
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null) {
            String display = name == null || name.isBlank() ? normalized.substring(0, normalized.indexOf('@')) : name.trim();
            user = new User(display, normalized, role);
            tempPassword = temporaryPassword();
            user.setPasswordHash(encoder.encode(tempPassword));
            users.save(user);
        }
        Membership m = memberships.save(new Membership(user, organizations.getReferenceById(me.orgId()), LocalDate.now(ZoneOffset.UTC)));
        String orgName = organizations.findById(me.orgId()).map(Organization::getName).orElse("Edocs");
        String wire = me.name() + " invited you to " + orgName + " on Edocs as " + role.name().toLowerCase() + "."
                + (tempPassword == null ? " Sign in with your existing account." : " Temporary password: " + tempPassword + " (change it after signing in).")
                + " " + props.frontendUrl() + "/login";
        notifications.send(me.orgId(), Recipient.user(user.getId(), user.getEmail()), NotificationType.INVITE, Channel.EMAIL,
                "You're invited to " + orgName, "You were invited to " + orgName + ".", "/login", wire);
        notifications.send(me.orgId(), Recipient.user(me.userId(), me.email()), NotificationType.INVITE, Channel.IN_APP, "Invitation sent",
                user.getEmail() + " was invited as " + role.name().toLowerCase() + ". The invitation email is queued.", "/settings");
        audit.record(AuditKind.SHARE, "Member invited: " + user.getEmail() + " (" + role.name().toLowerCase() + ")", null);
        return UserDto.of(m);
    }

    @Transactional
    public UserDto update(String id, UpdateMemberRequest patch) {
        AuthUser me = CurrentUser.get();
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("That member does not exist.");
        }
        boolean self = userId.equals(me.userId());
        boolean onlyMfa = patch.role() == null && patch.active() == null && patch.mfaEnabled() != null;
        if (!(self && onlyMfa)) {
            CurrentUser.require(Permission.MEMBERS_MANAGE);
        }
        if (self && ((patch.role() != null && patch.role() != Role.ADMIN && me.role() == Role.ADMIN) || Boolean.FALSE.equals(patch.active()))) {
            throw ApiException.conflict("You can’t remove your own administrator access.");
        }
        Membership m = memberships.findByOrgIdAndUserId(me.orgId(), userId).orElseThrow(() -> ApiException.notFound("That member does not exist."));
        User u = m.getUser();
        if (patch.role() != null) {
            u.setRole(patch.role());
        }
        if (patch.active() != null) {
            m.setActive(patch.active());
        }
        if (patch.mfaEnabled() != null) {
            u.setMfaEnabled(patch.mfaEnabled());
        }
        principals.evict(me.orgId(), userId);
        audit.record(AuditKind.KEY_ROTATION, "Member updated: " + u.getEmail(), null);
        return UserDto.of(m);
    }

    private String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
