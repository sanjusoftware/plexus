# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Download dependencies (cached layer)
# We use 'dependencies' task instead of 'build' for cleaner caching
RUN ./gradlew help --no-daemon

# Copy source and build
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# STAGE 2: Create the final lean image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Security: Run as non-root
RUN useradd -ms /bin/bash bankuser

# Copy the JAR from the 'build' stage
COPY --from=build /app/build/libs/*.jar app.jar

USER bankuser

# Expose the port
EXPOSE 8080

# Run with optimized JVM settings for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]