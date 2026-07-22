# Learning & Development (T9)

Talent Management milestone T9 — Volume 5 of the EWOS Business Bible.

## Scope

- **Course Catalogue** — internal / external / online / hybrid / on-the-job delivery.
- **Learning Paths** — sequences of courses with display order + mandatory flag.
- **Internal / External Training** — via `delivery_mode`.
- **Training Calendar** — window query on scheduled sessions.
- **Nomination** — per-employee nomination record (idempotent lifecycle).
- **Enrollment** — nomination → enrollment → in-progress → completed / failed / withdrawn / no-show.
- **Attendance** — attendance % on the enrollment.
- **Assessments** — score + pass/fail on the enrollment.
- **Certifications** — issued to an employee with reference + certificate URI.
- **Certification Expiry** — active-with-expiry index + `/reports/expiring` endpoint + mark-expired transition.
- **Dashboard, Reports** — aggregate counts and expiring-certifications view.

## Data model

Migration `V29__learning_development.sql`.

- `training_courses` — catalogue
- `learning_paths` + `learning_path_courses` — sequenced course paths
- `training_sessions` — scheduled instances (calendar)
- `training_enrollments` — per-employee lifecycle
- `certifications` — issued certifications with expiry tracking

All tables carry standard `tenant_id`, `company_id`, `version_no`, and soft-delete columns.

## Statuses

**Session** — `SCHEDULED` → `IN_PROGRESS` → `COMPLETED` / `CANCELLED`.

**Enrollment** — `NOMINATED` → `ENROLLED` → `IN_PROGRESS` → `COMPLETED` / `WITHDRAWN` / `NO_SHOW` / `FAILED`.

**Certification** — `ACTIVE` → `EXPIRED` / `REVOKED`.

## Endpoints

Under `/api/v1/training-courses`, `/api/v1/learning-paths`, `/api/v1/training-sessions`, `/api/v1/training-enrollments`, `/api/v1/certifications`, and `/api/v1/learning`.

Every endpoint requires `X-Tenant-Id` and a `LEARNING_*` authority.

## Permissions

| Code | Purpose |
| --- | --- |
| `LEARNING_READ` | View catalogue, paths, sessions, enrollments, certifications, dashboards |
| `LEARNING_WRITE` | Create / update courses, paths, sessions |
| `LEARNING_NOMINATE` | Nominate employees for training |
| `LEARNING_ENROLL` | Enroll / withdraw employees |
| `LEARNING_ATTEND` | Record attendance + assessment |
| `LEARNING_CERTIFY` | Issue / revoke certifications |
| `LEARNING_ADMIN` | Administer the learning module |

All seven are seeded onto `SYSTEM_ADMIN` by the V29 migration.

## Events

`LearningEvent` is published on `ewos.learning.event` when Kafka is enabled
(`app.messaging.kafka.enabled=true`). Event types cover course, path, session, enrollment lifecycle
transitions, plus certification issue / revoke / expiry.
