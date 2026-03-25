# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS build

ARG HTTP_PROXY=""
ARG HTTPS_PROXY=""
ARG NO_PROXY=""

# --- DRY PROXY CLEANING ---
# We use a single RUN command to set up the environment.
# Since ENV doesn't support complex shell logic, we use this trick:
ENV http_proxy=$HTTP_PROXY
ENV https_proxy=$HTTPS_PROXY
ENV no_proxy=$NO_PROXY

WORKDIR /app

# 1. Install Node.js & Setup Logic
# We extract the Host and Port ONCE and store them for the rest of the build.
RUN apt-get update && apt-get install -y curl sed && \
    export P_HOST=$(echo $HTTP_PROXY | sed -e 's|https*://||' -e 's|:.*||') && \
    export P_PORT=$(echo $HTTP_PROXY | sed -e 's|.*:||') && \
    echo "if [ -n \"\$HTTP_PROXY\" ]; then \
            export GRADLE_OPTS=\"-Dhttp.proxyHost=$P_HOST -Dhttp.proxyPort=$P_PORT -Dhttps.proxyHost=$P_HOST -Dhttps.proxyPort=$P_PORT\"; \
          fi; \
          ./gradlew \"\$@\"" > /usr/local/bin/gradle-proxy && \
    chmod +x /usr/local/bin/gradle-proxy && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# 2. Copy Gradle wrapper
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# 3. Cache dependencies
RUN gradle-proxy help --no-daemon

# 4. Copy source and build
COPY src src
RUN gradle-proxy bootJar -x test --no-daemon


# STAGE 2: Final Image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd -ms /bin/bash bankuser
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown bankuser:bankuser app.jar

USER bankuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]