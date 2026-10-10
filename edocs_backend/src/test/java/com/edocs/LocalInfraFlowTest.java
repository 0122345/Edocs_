package com.edocs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

// Same journeys on locally installed PostgreSQL, MongoDB, RabbitMQ and Mailpit (EDOCS_IT_LOCAL=true), using isolated edocs_it stores.
@EnabledIfEnvironmentVariable(named = "EDOCS_IT_LOCAL", matches = "true")
class LocalInfraFlowTest extends AbstractFlowTest {

    private static final String DB_URL = env("EDOCS_IT_DB_URL", "jdbc:postgresql://localhost:5432/edocs_it");
    private static final String MONGO_URI = env("EDOCS_IT_MONGO_URI", "mongodb://localhost:27017/edocs_it");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) throws Exception {
        resetStores();
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", () -> env("EDOCS_IT_DB_USER", "edocs"));
        registry.add("spring.datasource.password", () -> env("EDOCS_IT_DB_PASSWORD", "edocs"));
        registry.add("spring.mongodb.uri", () -> MONGO_URI);
        registry.add("spring.rabbitmq.host", () -> env("EDOCS_IT_RABBIT_HOST", "localhost"));
        registry.add("spring.rabbitmq.username", () -> env("EDOCS_IT_RABBIT_USER", "edocs"));
        registry.add("spring.rabbitmq.password", () -> env("EDOCS_IT_RABBIT_PASSWORD", "edocs"));
        registry.add("spring.rabbitmq.virtual-host", () -> env("EDOCS_IT_RABBIT_VHOST", "edocs_it"));
        registry.add("spring.mail.port", () -> env("EDOCS_IT_MAIL_PORT", "1025"));
    }

    // Journeys expect a freshly seeded workspace, so both stores start empty on every run.
    private static void resetStores() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, env("EDOCS_IT_DB_USER", "edocs"), env("EDOCS_IT_DB_PASSWORD", "edocs"));
                Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
        try (MongoClient mongo = MongoClients.create(MONGO_URI)) {
            mongo.getDatabase("edocs_it").drop();
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
