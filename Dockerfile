# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon > /dev/null

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon && \
    cp build/libs/*.jar /app/arkana-api.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system arkana && \
    useradd --system --gid arkana --no-create-home arkana

COPY --from=builder --chown=arkana:arkana /app/arkana-api.jar app.jar

USER arkana

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
