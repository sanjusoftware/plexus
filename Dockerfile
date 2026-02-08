# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Download dependencies (cached layer)
RUN ./gradlew build -x test --continue > /dev/null 2>&1 || true

# Copy source and build
COPY src src
RUN ./gradlew bootJar -x test

# STAGE 2: Create the final lean image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Security: Run as non-root
RUN useradd -ms /bin/bash bankuser
USER bankuser

# Copy the JAR from the 'build' stage above
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the port
EXPOSE 8080

# Run with optimized JVM settings for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]