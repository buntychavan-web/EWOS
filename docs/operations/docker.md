# Docker image guide

Everything the runtime image needs and none of what it doesn't. This page explains why the
`Dockerfile` looks the way it does and how to operate it.

## Layout

Two stages:

1. **`build`** — `maven:3.9-eclipse-temurin-21`. Downloads dependencies from the pinned `pom.xml`
   first, then compiles sources, then packages the fat jar. The two steps are separate so a
   code-only change reuses the dependency layer.
2. **`runtime`** — `eclipse-temurin:21-jre-alpine`. JRE only (no JDK, no build tools). Adds
   `tini` for signal handling and `curl` for the healthcheck.

## Why layered jars

Spring Boot 3.x emits a layered fat jar by default. The Dockerfile calls `layertools extract` so
each layer becomes its own `COPY` line in the runtime stage:

```
dependencies/          <- ~50 MB, changes only on pom.xml bumps
spring-boot-loader/    <- ~256 KB, essentially static
snapshot-dependencies/ <- empty for release builds
application/           <- ~1 MB, changes on every commit
```

Docker caches layers top-down. A code-only commit invalidates only `application/`, so the pushed
image delta is a couple of megabytes even though the total image is ~250 MB.

## Non-root by default

- User `ewos` is created with fixed numeric UID/GID `10001:10001`.
- The `USER 10001:10001` directive uses the numeric form so Kubernetes `runAsUser` / `runAsGroup`
  security contexts match without needing to resolve `/etc/passwd`.
- The `--chown=ewos:ewos` on every `COPY` avoids a separate `chown` layer.

## Signal handling

`tini` runs as PID 1 and reaps zombies, forwards `SIGTERM`, and cleans up on exit. Without it the
JVM sits behind a `sh -c` and never sees the container-stop signal — the orchestrator kills it
hard after the grace period.

## Container-aware JVM

`JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` is the default. The JVM sizes its
heap from the cgroup memory limit, and an OOM kills the process instead of leaving it in a bad
state. Operators can override `JAVA_OPTS` at runtime without rebuilding.

## Building

```bash
# Prod image with BuildKit cache mounts. BuildKit is on by default in modern
# Docker; the syntax=docker/dockerfile:1.7 header enables it explicitly.
docker build -t ewos:local .

# Push
docker tag ewos:local ghcr.io/<org>/ewos:<sha>
docker push ghcr.io/<org>/ewos:<sha>
```

## Running

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e APP_SECURITY_JWT_SECRET=... \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ewos \
  -e SPRING_DATASOURCE_USERNAME=ewos \
  -e SPRING_DATASOURCE_PASSWORD=... \
  ewos:local
```

## Healthcheck

```
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
```

- `start-period=45s` gives Flyway migrations time to finish before the first probe.
- We probe `/liveness`, not `/health`, so we're not tied to database availability at startup — the
  Kubernetes `readiness` probe should point at `/actuator/health/readiness` instead.

## `.dockerignore`

`.dockerignore` keeps `target/`, `.git/`, `.idea/`, `docs/`, and env files out of the build
context. A clean build context is why every image build hashes deterministically.

## What NOT to do

- Don't add `apk upgrade` — pin the base image tag instead.
- Don't ship a JDK in the runtime image; you don't need `javac` in production.
- Don't run as root, and don't `chmod 777` anything to "fix" a permission error. Find the file
  ownership issue and fix it in the `COPY --chown` line.
