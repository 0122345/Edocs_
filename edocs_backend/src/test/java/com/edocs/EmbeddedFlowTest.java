package com.edocs;

import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

import com.edocs.support.EmbeddedInfrastructure;

// Same journeys on in-process PostgreSQL, MongoDB-wire and AMQP servers, for machines without Docker.
@DisabledIf("dockerAvailable")
class EmbeddedFlowTest extends AbstractFlowTest {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        EmbeddedInfrastructure.start().forEach((k, v) -> registry.add(k, () -> v));
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
