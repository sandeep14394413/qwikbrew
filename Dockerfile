# Multi-stage Dockerfile — reused for all 6 QwikBrew services
# Build:   docker build --build-arg SERVICE=user-service -t qwikbrew/user-service .
# The pipeline passes SERVICE automatically for each matrix job.

# ── Stage 1: Build JAR ────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
ARG SERVICE
WORKDIR /workspace

# Cache dependency downloads (layer only invalidates when pom.xml changes)
COPY pom.xml .
COPY ${SERVICE}/pom.xml ${SERVICE}/pom.xml
RUN mvn -pl ${SERVICE} dependency:go-offline -q

# Build the JAR
COPY ${SERVICE}/src ${SERVICE}/src
RUN mvn -pl ${SERVICE} package -DskipTests -q \
    && mv ${SERVICE}/target/*.jar /workspace/app.jar

# ── Stage 2: Minimal runtime (distroless JRE 17 — no shell, no package manager)
FROM gcr.io/distroless/java17-debian12:nonroot
ARG SERVICE
WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

# JVM flags optimised for containerised workloads
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
