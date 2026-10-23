# ==========================================
# STAGE 1: Build Java Spring Boot Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Grant execute permission to mvnw
RUN chmod +x ./mvnw

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and package executable jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# STAGE 2: Lightweight Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security best practice
RUN addgroup -S wayfare && adduser -S wayfare -G wayfare

# Create runtime directories with permissions
RUN mkdir -p /app/uploads /app/logs && chown -R wayfare:wayfare /app

# Copy built jar from builder
COPY --from=builder --chown=wayfare:wayfare /build/target/*.jar /app/app.jar

# Dynamic port for Railway/Cloud deployments
ENV PORT=8081
EXPOSE 8081

USER wayfare

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
