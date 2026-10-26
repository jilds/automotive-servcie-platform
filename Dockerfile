# syntax=docker/dockerfile:1
# ASP - Automotive Service Platform: one image with the Spring Boot backend and the Angular app (ADR 0001).
# The tests are not run here: they need Docker (Testcontainers). CI runs ./mvnw clean verify before building this.
#   docker build -t asp .

FROM eclipse-temurin:26-jdk-noble@sha256:26d6a81be420aa8debd8618b58b27755663ba36f2418d7196e91f1c0aedc1809 AS build
WORKDIR /src
# The wrapper and the poms first, so dependency downloads stay cached while only sources change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY frontend/pom.xml frontend/package.json frontend/package-lock.json frontend/
COPY backend/pom.xml backend/
# A Windows checkout may hand over CRLF line endings; the wrapper must run under Linux.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY frontend/ frontend/
COPY backend/ backend/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    ./mvnw -B -ntp clean package -DskipTests \
 && java -Djarmode=tools -jar backend/target/backend-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:26-jre-noble@sha256:7f09cd24daab29ca40ee171f8a8c77c78524ca80f489e1d97aacb9a16f9af67e
LABEL org.opencontainers.image.title="ASP - Automotive Service Platform" \
      org.opencontainers.image.source="https://github.com/jilds/automotive-servcie-platform"
RUN groupadd --system --gid 10001 asp && useradd --system --uid 10001 --gid asp --no-create-home asp
WORKDIR /app
# Least-changing layers first. Owned by root: the app (user asp) can read its classes but not change them.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER asp
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# 8080: the app, reached only through the proxy. 8081: health and metrics, never published.
EXPOSE 8080 8081
# No curl in the runtime image: bash's /dev/tcp asks the readiness probe on the management port.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${ASP_MANAGEMENT_PORT:-8081} && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
