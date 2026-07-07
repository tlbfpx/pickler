# Multi-stage Dockerfile for Hey Pickler Backend (Spring Boot 3.2 / Java 17)
# Loop-v16B — adds container build infrastructure.

# ──────────────── Stage 1: build ────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -ntp -e -DskipTests dependency:go-offline

# Now build the jar
COPY src ./src
RUN mvn -B -ntp -DskipTests package \
    && cp target/hey-pickler-server-*.jar /workspace/app.jar

# ──────────────── Stage 2: runtime ────────────────
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Non-root user
RUN groupadd --system app && useradd --system --gid app app
USER app

# Application jar
COPY --from=build /workspace/app.jar /app/app.jar

# Container defaults — override at runtime via -e or compose/env
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Health check (uses the actuator if enabled; fallback to TCP echo)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
