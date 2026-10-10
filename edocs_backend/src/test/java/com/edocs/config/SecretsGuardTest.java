package com.edocs.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecretsGuardTest {

    private static final String REAL_JWT = "Zk3q9V0pL2mX7rT4wY8bN1cH6dJ5sA0eQ9uI3oP7gF2";
    private static final String REAL_MASTER = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String DEV_JWT = "dev-only-secret-change-me-dev-only-secret-change-me-0123456789";

    private static EdocsProperties props(String jwt, String master) {
        return new EdocsProperties(null, null, new EdocsProperties.Security(jwt, Duration.ofHours(2), 5, Duration.ofMinutes(15), ""),
                new EdocsProperties.Crypto(master), null, null, null);
    }

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void acceptsRealKeysOutsideDev() {
        assertThatCode(() -> new SecretsGuard(props(REAL_JWT, REAL_MASTER), profile())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrPublishedJwtSecretOutsideDev() {
        assertThatThrownBy(() -> new SecretsGuard(props("", REAL_MASTER), profile())).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new SecretsGuard(props(DEV_JWT, REAL_MASTER), profile())).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new SecretsGuard(props("change-me-at-least-32-characters-long-random-string", REAL_MASTER), profile("prod")))
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsMissingOrPublishedMasterKeyOutsideDev() {
        assertThatThrownBy(() -> new SecretsGuard(props(REAL_JWT, ""), profile())).hasMessageContaining("MASTER_KEY");
        assertThatThrownBy(() -> new SecretsGuard(props(REAL_JWT, SecretsGuard.PUBLISHED_MASTER_KEY), profile())).hasMessageContaining("MASTER_KEY");
    }

    @Test
    void allowsPublishedDevKeysInDevProfile() {
        assertThatCode(() -> new SecretsGuard(props(DEV_JWT, SecretsGuard.PUBLISHED_MASTER_KEY), profile("dev"))).doesNotThrowAnyException();
    }
}
