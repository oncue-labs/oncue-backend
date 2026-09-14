package com.oncue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DeploymentConfigurationTest {

    @Test
    void dockerfileBuildsWithJava21AndRunsOnJava21Jre() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertAll(
                () -> assertTrue(dockerfile.contains("FROM gradle:8.10.2-jdk21 AS build")),
                () -> assertTrue(dockerfile.contains("RUN ./gradlew bootJar --no-daemon")),
                () -> assertTrue(dockerfile.contains("FROM eclipse-temurin:21-jre")),
                () -> assertTrue(dockerfile.contains("COPY --from=build /workspace/build/libs/*.jar /app/oncue-backend.jar")),
                () -> assertTrue(dockerfile.contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/oncue-backend.jar\"]")));
    }

    @Test
    void actuatorDependencyAndExposureOnlyIncludeHealth() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));
        String applicationConfiguration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertAll(
                () -> assertTrue(buildScript.contains("spring-boot-starter-actuator")),
                () -> assertTrue(applicationConfiguration.contains("management:")),
                () -> assertTrue(applicationConfiguration.contains("include: health")));
    }

    @Test
    void dockerfileRunsAsNonRootAndDeclaresHealthcheck() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertAll(
                () -> assertTrue(dockerfile.contains("USER oncue")),
                () -> assertTrue(dockerfile.contains("HEALTHCHECK")),
                () -> assertTrue(dockerfile.contains("/actuator/health")));
    }

    @Test
    void actuatorHealthDoesNotExposeDependencyDetails() throws IOException {
        String applicationConfiguration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertAll(
                () -> assertTrue(applicationConfiguration.contains("show-details: never")),
                () -> assertTrue(applicationConfiguration.contains("db:")),
                () -> assertTrue(applicationConfiguration.contains("redis:")));
    }
}
