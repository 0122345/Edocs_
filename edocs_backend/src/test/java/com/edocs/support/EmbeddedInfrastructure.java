package com.edocs.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.SystemLauncher;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

// In-process PostgreSQL, MongoDB-wire and AMQP 0-9-1 servers for machines without Docker. Started once per JVM.
public final class EmbeddedInfrastructure {

    private static Map<String, String> properties;

    private EmbeddedInfrastructure() {
    }

    public static synchronized Map<String, String> start() {
        if (properties != null) {
            return properties;
        }
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            MongoServer mongo = new MongoServer(new MemoryBackend());
            InetSocketAddress mongoAddress = mongo.bind();
            int amqpPort = freePort();
            startQpid(amqpPort);

            Map<String, String> p = new HashMap<>();
            p.put("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
            p.put("spring.datasource.username", "postgres");
            p.put("spring.datasource.password", "postgres");
            p.put("spring.mongodb.uri", "mongodb://localhost:" + mongoAddress.getPort() + "/edocs");
            p.put("spring.rabbitmq.host", "localhost");
            p.put("spring.rabbitmq.port", Integer.toString(amqpPort));
            p.put("spring.rabbitmq.username", "guest");
            p.put("spring.rabbitmq.password", "guest");
            p.put("edocs.messaging.dead-lettering", "false");
            // mongo-java-server does not implement the `hello` command used by the Mongo health check.
            p.put("management.health.mongo.enabled", "false");
            p.put("management.health.mongodb.enabled", "false");
            properties = Map.copyOf(p);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                mongo.shutdownNow();
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // Best effort on JVM exit.
                }
            }));
            return properties;
        } catch (Exception e) {
            throw new IllegalStateException("Could not start embedded infrastructure", e);
        }
    }

    private static void startQpid(int port) throws Exception {
        String workDir = Files.createTempDirectory("qpid").toString();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("type", "Memory");
        attributes.put("initialConfigurationLocation", EmbeddedInfrastructure.class.getResource("/qpid-embedded.json").toExternalForm());
        attributes.put("startupLoggedToSystemOut", false);
        attributes.put("context", Map.of("qpid.amqp_port", Integer.toString(port), "qpid.work_dir", workDir, "qpid.home_dir", workDir));
        SystemLauncher launcher = new SystemLauncher();
        launcher.startup(attributes);
        Runtime.getRuntime().addShutdownHook(new Thread(launcher::shutdown));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
