# Multi-stage build for the klinekit Spring Boot API.
# Stage 1: build the executable jar with Gradle on JDK 21.
# Stage 2: run it on a minimal JRE.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Cache layer: only re-resolve dependencies when build files change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY core/build.gradle.kts core/build.gradle.kts
COPY persistence/build.gradle.kts persistence/build.gradle.kts
COPY api/build.gradle.kts api/build.gradle.kts
COPY cli/build.gradle.kts cli/build.gradle.kts
RUN ./gradlew --no-daemon :api:dependencies > /dev/null || true

# Sources
COPY core/src core/src
COPY persistence/src persistence/src
COPY api/src api/src
COPY cli/src cli/src

RUN ./gradlew --no-daemon :api:bootJar -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/api/build/libs/*.jar /app/klinekit-api.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/v3/api-docs > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/klinekit-api.jar"]
