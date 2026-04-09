# ============================================================
# YoSnowMow — Backend Dockerfile (multi-stage build)
# ============================================================
# Stage 1: Build the Spring Boot JAR using Maven
# Stage 2: Run the JAR in a slim JRE 21 image
#
# Build locally:
#   docker build -t yosnowmow-api ./backend
#
# Run locally (after building):
#   docker run -p 8080:8080 \
#     -e SPRING_PROFILES_ACTIVE=dev \
#     -e FIREBASE_PROJECT_ID=yosnowmow-dev \
#     -e FIREBASE_SERVICE_ACCOUNT_PATH=/secrets/sa.json \
#     yosnowmow-api
# ============================================================

# ---- Stage 1: Build ----------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first so dependency layer is cached independently
# of source changes — speeds up rebuilds significantly.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build the JAR
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Runtime --------------------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy only the built JAR from the builder stage — no Maven,
# no source code, no intermediate build artifacts in the image.
COPY --from=builder /app/target/*.jar app.jar

# Cloud Run injects PORT env var; Spring Boot defaults to 8080.
# Explicitly expose 8080 to document the expected port.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
