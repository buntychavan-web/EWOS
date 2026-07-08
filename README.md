# EWOS — Enterprise Workforce Operating System

Backend for the EWOS HRMS platform. Sprint 1 delivered the project
foundation; Sprint 2 adds the identity module (users, roles, permissions,
JWT login, refresh-token rotation). No HRMS domain modules (Employee,
Payroll, Leave, Attendance, Organization) have been implemented yet.

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
│   ├── JpaConfig.java
│   ├── OpenApiConfig.java
│   └── RedisConfig.java
├── security/                         # security foundation
│   ├── SecurityConfig.java
│   └── jwt/
│       ├── JwtProperties.java
│       ├── JwtService.java
│       └── JwtAuthenticationFilter.java
├── common/
│   ├── exception/                    # global error handling
│   │   ├── ApiError.java
│   │   ├── ApiException.java
│   │   └── GlobalExceptionHandler.java
│   └── persistence/
│       └── AuditableEntity.java      # UUID id + created/updated auditing
└── identity/                         # Sprint 2 — users, roles, permissions
    ├── api/
    │   ├── AuthController.java
    │   └── dto/
    │       ├── LoginRequest.java
    │       ├── RefreshRequest.java
    │       └── TokenResponse.java
    ├── application/
    │   ├── AuthenticationService.java
    │   ├── BootstrapProperties.java
    │   └── IdentityBootstrap.java
    ├── domain/
    │   ├── Permission.java
    │   ├── Role.java
    │   ├── User.java
    │   └── RefreshToken.java
    └── infrastructure/
        └── persistence/
            ├── PermissionRepository.java
            ├── RoleRepository.java
            ├── UserRepository.java
            └── RefreshTokenRepository.java

src/main/resources
├── application.yml                   # base config
├── application-dev.yml               # dev profile
├── application-test.yml              # test profile
├── application-prod.yml              # prod profile
├── logback-spring.xml                # logging config
└── db/migration/
    ├── V1__baseline.sql              # extensions
    ├── V2__create_identity_tables.sql
    └── V3__seed_default_roles_permissions.sql
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
| `ADMIN_USERNAME`                | Default admin username               | `admin`                    |
| `ADMIN_EMAIL`                   | Default admin email                  | `admin@ewos.local`         |
| `ADMIN_PASSWORD`                | Default admin bootstrap password     | `ChangeMe!Admin123`        |

**Production** requires an externally provided `JWT_SECRET` of at least 256
bits, plus an `ADMIN_PASSWORD` set explicitly at first boot. The default
values are placeholders and must not be used outside local development.

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
  `/swagger-ui/**`, `/api/v1/auth/login`, `/api/v1/auth/refresh`.
- Access tokens are HS256-signed JWTs carrying `sub` (user id) and an
  `authorities` claim (`ROLE_<name>` plus each permission code).
- Refresh tokens are opaque, high-entropy strings persisted as SHA-256
  hashes in `refresh_tokens` and **rotated on every use**; the presented
  token is revoked as the new pair is issued, so reuse is detected on
  the next call.
- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`,
  validates the signature and issuer via `JwtService`, and populates
  the `SecurityContext` with the JWT's authorities.
- Passwords are hashed with BCrypt via the shared `PasswordEncoder` bean.

### Identity endpoints

| Method | Path                    | Purpose                                             |
| ------ | ----------------------- | --------------------------------------------------- |
| POST   | `/api/v1/auth/login`    | Exchange username + password for an access/refresh pair |
| POST   | `/api/v1/auth/refresh`  | Rotate a refresh token for a new pair               |

### Default administrator

On first boot `IdentityBootstrap` creates a `SYSTEM_ADMIN` user from
`app.security.bootstrap.admin.*` if none exists. In non-dev environments
override `ADMIN_PASSWORD` (and typically `ADMIN_USERNAME` / `ADMIN_EMAIL`)
before the app starts, and rotate immediately after first login. The
bootstrap is idempotent — it never touches an existing user.

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
- Self-service registration / password reset flows
- Multi-tenant / organization scoping of users
- Account lockout, password-policy enforcement, MFA
