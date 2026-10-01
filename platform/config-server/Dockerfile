# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY platform ./platform
COPY services ./services
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl platform/config-server -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/platform/config-server/target/config-server-0.1.0-SNAPSHOT.jar app.jar
COPY config-repo /app/config-repo
ENV CONFIG_REPO_LOCATION=file:/app/config-repo/
USER app
EXPOSE 8888
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl --fail --silent http://localhost:8888/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
