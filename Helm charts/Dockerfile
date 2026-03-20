# ─────────────────────────────────────────────────────────
#  QwikBrew — Multi-stage Dockerfile (reuse for every service)
#  Usage: docker build --build-arg SERVICE=user-service -t qwikbrew/user-service .
# ─────────────────────────────────────────────────────────

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
ARG SERVICE
WORKDIR /workspace

# Cache dependency downloads separately
COPY pom.xml .
COPY ${SERVICE}/pom.xml ${SERVICE}/pom.xml
RUN mvn -pl ${SERVICE} dependency:go-offline -q

# Build the service JAR
COPY ${SERVICE}/src ${SERVICE}/src
RUN mvn -pl ${SERVICE} package -DskipTests -q

# Stage 2: Runtime (distroless for minimal attack surface)
FROM gcr.io/distroless/java17-debian12:nonroot
ARG SERVICE
WORKDIR /app

COPY --from=builder /workspace/${SERVICE}/target/*.jar app.jar

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
