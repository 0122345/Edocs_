package com.edocs;

import org.springframework.boot.SpringApplication;

import com.edocs.support.EmbeddedInfrastructure;

// Docker-free local run: `mvn spring-boot:test-run` starts embedded PostgreSQL, MongoDB and AMQP, then the API on :8080.
public class LocalDevApplication {

    public static void main(String[] args) {
        EmbeddedInfrastructure.start().forEach(System::setProperty);
        System.setProperty("spring.profiles.active", System.getProperty("spring.profiles.active", "dev"));
        SpringApplication.run(EdocsApplication.class, args);
    }
}
