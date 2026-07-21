# Identity module — 15-artifact charter compliance

**Module:** `com.ewos.identity`
**Reviewed on branch:** `claude/wp-003-foundation`
**Charter reference:** `docs/architecture/IMPLEMENTATION_ROADMAP.md` §"Per-module definition of done"

| # | Artifact | Present | Location |
|---|----------|---------|----------|
| 1 | Flyway migration | ✅ | `src/main/resources/db/migration/V2__create_identity_tables.sql`, V3, V4, V5, V6, V7 |
| 2 | Domain entity + value objects | ✅ | `com.ewos.identity.domain.{User, Role, Permission, LoginHistory, PasswordHistory, RefreshToken, LoginEventType, Permissions}` |
| 3 | Repository | ✅ | `com.ewos.identity.infrastructure.persistence.{UserRepository, RoleRepository, PermissionRepository, LoginHistoryRepository, PasswordHistoryRepository, RefreshTokenRepository, UserSpecifications}` |
| 4 | Request/response DTOs | ✅ | `com.ewos.identity.api.dto.*` (LoginRequest, RefreshRequest, CreateUserRequest, UpdateUserRequest, ChangePasswordRequest, ResetPasswordRequest, StatusRequest, UserResponse, TokenResponse, RoleSummary, UserSearchCriteria) |
| 5 | Mapper | ✅ (added in WP-003) | `com.ewos.identity.api.UserMapper` — explicit, reflection-free. |
| 6 | Application service | ✅ | `com.ewos.identity.application.{AuthenticationService, UserService, PasswordHistoryService, LoginHistoryRecorder, IdentityBootstrap}` |
| 7 | Domain service | ✅ | `PasswordPolicyValidator` (rule enforcement, config-driven via `PasswordPolicyProperties`). `AccountLockoutService` (in `identity.infrastructure.security.ratelimit`, but the policy itself is framework-neutral). |
| 8 | REST controller | ✅ | `AuthController`, `UserController`; each method carries `@PreAuthorize` (except `/auth/login`, `/auth/refresh` which are pre-auth by design). |
| 9 | Validation | ✅ | `jakarta.validation.constraints.*` on every DTO record component; `@Valid` on every controller parameter. |
| 10 | Exception handling | ✅ | Uses `com.ewos.shared.exception.ApiException`; global mapping via `GlobalExceptionHandler`. Sensitive fields scrubbed by `sensitize()`. |
| 11 | Audit | ✅ | All aggregates extend `AuditableEntity`; `AuditorProvider` reads the authenticated principal ID. |
| 12 | Security | ✅ | `SecurityConfig` (stateless, JWT); `JwtSecretGuard` refuses placeholders in `prod`; permissions seeded in V3/V7; rate-limit + account-lockout in `identity.infrastructure.security.ratelimit`. |
| 13 | OpenAPI | ✅ | `@Tag`, `@Operation`, `@ApiResponse`, `@Schema` on both controllers. Swagger UI exposed via springdoc under `/swagger-ui.html`. |
| 14 | Unit tests | ✅ | `AuthenticationServiceTest`, `UserServiceTest`, `PasswordPolicyValidatorTest`, `JwtServiceTest`, `JwtSecretGuardTest`, `AccountLockoutServiceTest`, `InMemoryRateLimiterTest`, `AuthRateLimitFilterTest`, `CorsConfigTest`, `GlobalExceptionHandlerTest`, `CorrelationIdFilterTest`, `AuditorProviderTest`, **`UserMapperTest`** (added in WP-003). |
| 15 | Integration tests | ✅ | `AuthControllerIntegrationTest`, `UserControllerIntegrationTest`, `CorsPreflightIntegrationTest`. All extend `AbstractIntegrationTest` (singleton Postgres via Testcontainers). |

## What changed in WP-003 for Identity

- Extracted `UserMapper` from `UserService.toResponse(User)` into a dedicated `@Component`. Charter artifact #5 was previously satisfied only implicitly.
- Injected `UserMapper` through the constructor of `UserService`; no field injection, no reflection.
- Added `UserMapperTest` (2 tests) covering full-field mapping + null-role handling.
- Moved everything from `com.ewos.security.*` into `com.ewos.identity.infrastructure.security.*` so the identity module owns its own security infra — matching the master arch domain boundary.

## Deliberate non-changes

- **`AuthenticationService`** already returns `TokenResponse` directly (no mapper needed); the shape is trivial.
- **`AuthController`/`UserController`** OpenAPI annotations were already in place — verified, no additions needed.
- **Password history reuse check** is a domain rule; kept inside `PasswordHistoryService` where it lives close to storage.

## Follow-ups explicitly deferred

- Split `AccountLockoutService` between a pure domain policy (framework-free) and its Spring-integration shell. Currently they're one class; when it grows a second policy dimension, split then.
- Extract a dedicated `AuthResponseMapper` if `TokenResponse` grows beyond three fields. It won't in the near term.
- No new Flyway migration added for Identity in WP-003. Any schema change is a new `V{N}__*.sql`.

## Verification

```
mvn -B compile spotless:check checkstyle:check pmd:check spotbugs:check   → BUILD SUCCESS, 0 findings
mvn -B test -Dtest='!*IntegrationTest,!EwosApplicationTests,!FlywayMigrationValidationTest'
    → Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
Integration tests (Docker required, CI-only): 25 tests.
```
