# EWOS — Testing Guide

_Added: 2026-07-28, Sprint 15 (Enterprise Quality & Reliability Sprint)._

This document describes how the backend test suite is organized, how to run
it, and where coverage currently stands. It complements `PROJECT_STATUS.md`
§4 (which tracks the JaCoCo gate and coverage roadmap) rather than
duplicating it — this file is the "how", that section is the "how much".

---

## 1. Test types in this repo

| Type | Base class / pattern | Needs Docker? | What it verifies |
| --- | --- | --- | --- |
| Unit (mocks) | plain JUnit 5 + Mockito, no Spring context | No | One class's logic in isolation — the large majority of the suite |
| Domain (no mocks) | plain JUnit 5, `new SomeCalculator()` | No | Framework-neutral domain classes (calculators, policies, validators) exercised directly, no mocking needed |
| Integration | extends `AbstractIntegrationTest` (`@SpringBootTest` + Testcontainers Postgres) | **Yes** | Real Spring context, real SQL, real Hibernate mapping — the only way to catch boot-time wiring bugs and SQL-level regressions (see §3) |

Run `mvn -q test -Dtest='!**/*IntegrationTest,!**/*RegressionTest'` to skip
everything that needs Docker when iterating locally without it. Note this
undercounts by a few classes whose names don't end in `IntegrationTest`
(e.g. `SoftDeleteRegressionTest`, `EwosApplicationTests`) — check
`AbstractIntegrationTest` subclasses explicitly if you need a fully clean
Docker-free run.

## 2. Running the suite

```bash
# Full suite, all gates (needs Docker)
mvn -q verify

# Unit + domain tests only, no Docker required
mvn -q test -Dtest='!**/*IntegrationTest,!**/*RegressionTest'

# One class
mvn -q test -Dtest=PayrollCalculatorTest

# Format, then check style/static-analysis without running tests
mvn -q spotless:apply
mvn -q checkstyle:check pmd:check
```

CI (`.github/workflows/ci.yml`) runs Spotless, Checkstyle, PMD, SpotBugs, and
`mvn verify` (which includes `jacoco:check`) on every push/PR to `ewos-main`.
Docker is preinstalled on the `ubuntu-latest` runner, so the full integration
suite runs there even when it can't run in a Docker-less sandbox locally.

## 3. Why some bugs only show up in the integration suite

A recurring lesson from this project's CI history (see `PROJECT_STATUS.md`
§0's P9 subsection): mocked unit tests can't catch bugs in the SQL Hibernate
actually generates, or in whether Spring can resolve a bean's constructor at
boot. `User`/`Role`/`Permission` soft-delete was broken for months because
the one test that would have caught it never ran against real Postgres in
CI. `SoftDeleteRegressionTest` and `EwosApplicationTests.contextLoads` are
deliberately real-Hibernate/real-Spring-context tests for exactly this
reason — don't "fix" them by mocking out the parts that make them useful.

## 4. Test-writing conventions used in this codebase

- **Unit tests for `@Service` classes**: `@ExtendWith(MockitoExtension.class)`,
  `@Mock` every collaborator (repositories, other services, `ClientAccessGuard`,
  `ApplicationEventPublisher`), construct the service under test by hand in
  `@BeforeEach` (services in this codebase take constructor injection only —
  no field injection to work around). See `PayComponentServiceTest` or
  `ProbationServiceTest` for the pattern.
- **Multi-tenant / multi-company guard verification**: every company-scoped
  service is expected to call `ClientAccessGuard.requireAccessForCompany(...)`
  (single record) or `requireAccessForCompanies(...)` (list results, called
  with every *distinct* company id in the result set) before touching data.
  Tests assert this with `verify(guard).requireAccessForCompany(companyId)`
  — see any `*ServiceTest` in `com.ewos.payroll.application` for ~20
  examples of the pattern applied consistently.
- **Actor-stamped mutations** (`approvedBy`, `confirmedBy`, `terminatedBy`,
  etc.): tests that exercise these set up
  `SecurityContextHolder.getContext().setAuthentication(new
  UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), "n/a",
  List.of()))` in `@BeforeEach` and `SecurityContextHolder.clearContext()` in
  `@AfterEach` — the service reads the authenticated principal's `getName()`
  as the actor UUID. Forgetting the `@AfterEach` clear leaks authentication
  into the next test class in the same JVM fork.
- **Positive, negative, boundary, and exception-handling coverage per class**
  — not just the happy path. Every service test added in Sprint 15 covers:
  cross-company/cross-tenant rejection, not-found (404), conflicting state
  transitions (409), and at least one case where a downstream collaborator
  throws mid-operation to verify the failure is handled (marked
  FAILED/rolled back) rather than silently swallowed or left in an
  inconsistent state.
- **Regression tests** live either next to the class they guard (e.g.
  `StatutoryDeductionServiceTest` covers the in-run dedup fix inline) or in
  `com.ewos.shared`/`com.ewos.identity.infrastructure.persistence` for
  cross-cutting bugs (`ConstructorAmbiguityRegressionTest`,
  `SoftDeleteRegressionTest`) that don't belong to one feature module.

## 5. Coverage

See `PROJECT_STATUS.md` §4 for the current JaCoCo floor, the instruction-
coverage percentage as of the last CI run that reached `jacoco-check`, and
the staged 35%→50%→65%→80% roadmap. Don't add tests purely to move that
number — a meaningless assertion that only touches a getter/setter doesn't
belong in this suite. Every test should be able to fail for a real reason.
