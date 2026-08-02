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

RUN mkdir -p /home/arkana/.postgresql

COPY src/main/resources/certs/supabase-prod-ca-2021.crt /home/arkana/.postgresql/root.crt

RUN chown arkana:arkana /home/arkana/.postgresql/root.crt && \
    chmod 600 /home/arkana/.postgresql/root.crt \
    
COPY --from=builder --chown=arkana:arkana /app/arkana-api.jar app.jar

USER arkana

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
