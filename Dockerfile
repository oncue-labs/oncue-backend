FROM gradle:8.10.2-jdk21 AS build

WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system oncue \
    && useradd --system --gid oncue --home-dir /nonexistent --shell /usr/sbin/nologin oncue

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/oncue-backend.jar
RUN chown -R oncue:oncue /app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

USER oncue
ENTRYPOINT ["java", "-jar", "/app/oncue-backend.jar"]
