# syntax=docker/dockerfile:1.7

# ---- Build stage ---------------------------------------------------------
# Uses BuildKit cache mounts so ~/.m2/repository is reused across builds.
# The dependency-resolution step is kept separate from source compilation so
# a code-only change does not re-download the world.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Warm the local Maven repo from pom.xml only.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q -DskipTests dependency:go-offline

# 2. Copy sources and package. -DskipTests is deliberate: tests run in CI, not
#    on every image build.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q -DskipTests clean package

# 3. Expand the Spring Boot fat jar into its layered layout so each layer
#    can be copied separately into the runtime image. Rarely-changing layers
#    (dependencies, spring-boot-loader) get cached; the tiny application/
#    layer is the only one that changes per commit.
RUN java -Djarmode=layertools -jar target/ewos.jar extract --destination target/layers

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Signal handling (SIGTERM -> Java) and curl for the healthcheck.
RUN apk add --no-cache tini curl

# Non-root user with a fixed numeric UID/GID so Kubernetes SecurityContext
# runAsUser/runAsGroup values line up predictably.
RUN addgroup -S -g 10001 ewos && adduser -S -u 10001 -G ewos ewos

WORKDIR /app

# COPY each Spring Boot layer separately. The layer that changes most often
# (application/) is copied last so the previous layers stay cached.
COPY --from=build --chown=ewos:ewos /workspace/target/layers/dependencies/ ./
COPY --from=build --chown=ewos:ewos /workspace/target/layers/spring-boot-loader/ ./
COPY --from=build --chown=ewos:ewos /workspace/target/layers/snapshot-dependencies/ ./
COPY --from=build --chown=ewos:ewos /workspace/target/layers/application/ ./

USER 10001:10001
EXPOSE 8080

# Container-aware JVM defaults. -XX:MaxRAMPercentage lets the JVM size the
# heap from the cgroup memory limit; operators can still override JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE="prod"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# tini as PID 1 -> Java process receives real signals, gets a clean shutdown.
ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
