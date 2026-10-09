package com.edocs.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.edocs.config.EdocsProperties;
import com.edocs.identity.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// OAuth2 authorization-code login (Google / Microsoft): maps the provider identity to an invited member and hands the SPA an Edocs JWT.
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService auth;
    private final EdocsProperties props;

    public OAuth2LoginSuccessHandler(AuthService auth, EdocsProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        // Only a provider-verified email links to a member; preferred_username/upn are user-editable and never trusted.
        String email = verified(oauthUser) ? oauthUser.getAttribute("email") : null;
        boolean idpMfa = oauthUser.getAttribute("amr") instanceof Collection<?> amr && amr.contains("mfa");
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String target;
        try {
            String token = auth.loginWithProvider(email, idpMfa).token();
            // The token travels in the URL fragment, which browsers never send to servers or put in Referer headers.
            target = props.frontendUrl() + "/login#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            target = props.frontendUrl() + "/login?error=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
        }
        response.sendRedirect(target);
    }

    // Google sends email_verified; Microsoft sends xms_edov when the email domain is verified for the tenant.
    private static boolean verified(OAuth2User user) {
        return isTrue(user.getAttribute("email_verified")) || isTrue(user.getAttribute("xms_edov"));
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
}
