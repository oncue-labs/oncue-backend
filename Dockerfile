FROM gradle:8.10.2-jdk21 AS build

WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/oncue-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/oncue-backend.jar"]
