# syntax=docker/dockerfile:1.6

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Cache sbt deps
COPY project/build.properties project/
COPY project/plugins.sbt project/ 2>/dev/null || true
RUN echo "sbt.version=1.12.11" > project/build.properties
RUN mkdir -p project && [ -f project/plugins.sbt ] || echo "" > project/plugins.sbt

# sbt wrapper would normally live here; for simplicity we use a published sbt image layer
FROM sbtscala/scala-2.13:17.0.10 AS sbt-build
WORKDIR /build
COPY --from=build /build/project project
COPY build.sbt .
COPY src src
RUN --mount=type=cache,target=/root/.sbt \
    --mount=type=cache,target=/root/.cache/coursier \
    sbt assembly

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=sbt-build /build/target/scala-2.13/scala-akka-aws-microservice-assembly-*.jar /app/app.jar

# Run as non-root
RUN useradd -ms /bin/bash app
USER app

EXPOSE 8080
ENV PORT=8080
ENV STORAGE_BACKEND=dynamodb

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
