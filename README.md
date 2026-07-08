# EWOS — Enterprise Workforce Operating System

Production-ready foundation for the EWOS HRMS platform (Sprint 1). This
repository contains the project skeleton only — no HRMS domain modules
(Employee, Payroll, Leave, Attendance, Organization) have been implemented
yet. Those arrive in later sprints.

## Tech stack

| Concern            | Choice                                 |
| ------------------ | -------------------------------------- |
| Language           | Java 21                                |
| Framework          | Spring Boot 3.3                        |
| Build              | Maven                                  |
| Database           | PostgreSQL 16                          |
| Migrations         | Flyway                                 |
| Cache              | Redis 7 (config only)                  |
| Auth               | Spring Security + JWT (HS256)          |
| ORM                | JPA / Hibernate                        |
| API docs           | springdoc-openapi (Swagger UI)         |
| Observability      | Spring Boot Actuator                   |
| Tests              | JUnit 5 + Testcontainers               |
| Container runtime  | Docker + Docker Compose                |

## Project layout

```
src/main/java/com/ewos
├── EwosApplication.java              # entry point
├── config/                           # cross-cutting configuration
│   ├── OpenApiConfig.java
│   └── RedisConfig.java
├── security/                         # security foundation
│   ├── SecurityConfig.java
│   └── jwt/
│       ├── JwtProperties.java
│       ├── JwtService.java
│       └── JwtAuthenticationFilter.java
└── common/
    └── exception/                    # global error handling
        ├── ApiError.java
        ├── ApiException.java
        └── GlobalExceptionHandler.java

src/main/resources
├── application.yml                   # base config
├── application-dev.yml               # dev profile
├── application-test.yml              # test profile
├── application-prod.yml              # prod profile
├── logback-spring.xml                # logging config
└── db/migration/V1__baseline.sql     # Flyway baseline
```

HRMS bounded contexts will each live in their own `com.ewos.<context>`
package (e.g. `com.ewos.employee`) with the standard `api / application /
domain / infrastructure` sublayers, added in future sprints.

## Prerequisites

- JDK **21**
- Maven **3.9+**
- Docker **24+** and Docker Compose **v2**

## Quick start (Docker Compose)

Boots app + PostgreSQL + Redis together:

```bash
docker compose up --build
```

Once healthy:

- App:            http://localhost:8080
- Health:         http://localhost:8080/actuator/health
- Swagger UI:     http://localhost:8080/swagger-ui.html
- OpenAPI JSON:   http://localhost:8080/v3/api-docs

Stop and remove volumes:

```bash
docker compose down -v
```

## Local development (no container for the app)

Start Postgres + Redis only:

```bash
docker compose up -d postgres redis
```

Run the app on the `dev` profile:

```bash
./mvnw spring-boot:run
# or
mvn spring-boot:run
```

Package a fat jar:

```bash
mvn clean package
java -jar target/ewos.jar
```

## Configuration

Configuration is layered:

1. `application.yml` — shared defaults
2. `application-{dev,test,prod}.yml` — profile overrides
3. Environment variables — take precedence over everything

Key environment variables:

| Variable                        | Purpose                              | Default (dev)              |
| ------------------------------- | ------------------------------------ | -------------------------- |
| `SPRING_PROFILES_ACTIVE`        | Active Spring profile                | `dev`                      |
| `SPRING_DATASOURCE_URL`         | JDBC URL                             | `jdbc:postgresql://...`    |
| `SPRING_DATASOURCE_USERNAME`    | DB username                          | `ewos`                     |
| `SPRING_DATASOURCE_PASSWORD`    | DB password                          | `ewos`                     |
| `SPRING_DATA_REDIS_HOST`        | Redis host                           | `localhost`                |
| `SPRING_DATA_REDIS_PORT`        | Redis port                           | `6379`                     |
| `JWT_SECRET`                    | HMAC secret for signing tokens       | placeholder — replace      |
| `JWT_ISSUER`                    | JWT `iss` claim                      | `ewos`                     |
| `JWT_ACCESS_TTL`                | Access-token lifetime                | `15m`                      |
| `JWT_REFRESH_TTL`               | Refresh-token lifetime               | `7d`                       |

**Production** requires an externally provided `JWT_SECRET` of at least 256
bits. The default value is a placeholder and must not be used outside
local development.

## Profiles

- **dev** — local Postgres/Redis via Docker Compose; verbose SQL and
  security logging; Hikari pool sized for a laptop.
- **test** — Testcontainers Postgres, Redis auto-configuration disabled,
  Flyway on. Used by JUnit integration tests.
- **prod** — all connection details from environment; error responses
  hide message/stack; actuator exposes only `health` and `info`; rolling
  file logs under `${LOG_PATH:-/var/log/ewos}/ewos.log`.

## Security

- Stateless session policy; CSRF disabled; HTTP Basic and form login
  disabled.
- All endpoints require authentication except:
  `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`,
  `/swagger-ui/**`.
- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`,
  validates the signature and issuer via `JwtService`, and populates the
  `SecurityContext`.
- No `/login` or user store exists yet — that arrives with the identity
  module in Sprint 2.

## Database migrations

Flyway runs on startup from `classpath:db/migration`.

- Naming: `V<version>__<snake_case_description>.sql`
- Example: `V2__create_employee_table.sql`
- Baselining is on; the current baseline (`V1__baseline.sql`) only
  registers the `pgcrypto` and `uuid-ossp` extensions.

## Health checks

- Liveness:  `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Aggregate: `GET /actuator/health`

The Dockerfile wires the container `HEALTHCHECK` to the liveness probe.

## Tests

Integration tests extend `AbstractIntegrationTest`, which spins up a
`postgres:16-alpine` Testcontainer and points the datasource at it. Run
the suite with:

```bash
mvn test
```

Docker must be running on the host for Testcontainers to work.

## Coding standards

- Package-by-feature under `com.ewos.<context>` for future domain
  modules; foundation code stays in `config`, `security`, `common`.
- Constructor injection only; no field injection.
- Records for immutable configuration, DTOs, and value objects.
- No business logic in controllers or repositories.
- All error responses return `ApiError` produced by
  `GlobalExceptionHandler`.
- Migrations are append-only — never edit a merged migration.

## What is NOT in this sprint

- Employee, Payroll, Leave, Attendance, Organization modules
- Authentication endpoints (`/login`, `/refresh`)
- User / role persistence
- Any REST controllers beyond what Spring Boot provides
