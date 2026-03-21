# Multi-stage Dockerfile — reused for all 6 QwikBrew services
# Build:   docker build --build-arg SERVICE=user-service -t qwikbrew/user-service .
# The pipeline passes SERVICE automatically for each matrix job.

# ── Stage 1: Build JAR ────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
ARG SERVICE
WORKDIR /workspace

# Copy ALL module pom.xml files first — Maven reads the parent pom.xml which
# lists every module in <modules>. If any module pom is missing when Maven
# resolves the reactor, the build fails with "Child module does not exist".
# These layers only invalidate when a pom.xml changes, so caching still works.
COPY pom.xml                           pom.xml
COPY service-discovery/pom.xml         service-discovery/pom.xml
COPY api-gateway/pom.xml               api-gateway/pom.xml
COPY user-service/pom.xml              user-service/pom.xml
COPY menu-service/pom.xml              menu-service/pom.xml
COPY order-service/pom.xml             order-service/pom.xml
COPY payment-service/pom.xml           payment-service/pom.xml
COPY notification-service/pom.xml      notification-service/pom.xml

# Resolve dependencies for the target service only (-pl = project list)
# -am = also make upstream dependencies if any
RUN mvn -pl ${SERVICE} -am dependency:go-offline -q

# Copy source and build JAR
COPY ${SERVICE}/src ${SERVICE}/src
RUN mvn -pl ${SERVICE} package -DskipTests -q \
    && mv ${SERVICE}/target/*.jar /workspace/app.jar

# ── Stage 2: Minimal runtime (distroless JRE 17 — no shell, no package manager)
FROM gcr.io/distroless/java17-debian12:nonroot
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
