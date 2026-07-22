# Employee Exit & Alumni (T12)

Talent Management milestone T12 — Volume 5 of the EWOS Business Bible.

## Scope

- **Resignation** — employee-initiated exit request with reason, intended last day, notice period.
- **Notice Management** — start-of-notice, notice window (`notice_start_date`, `notice_end_date`).
- **Notice Buyout** — waive a portion of notice with days + amount.
- **Exit Workflow** — accept → in-notice → exited; withdraw/cancel side-branches.
- **Clearance Process** — per-department (IT, Finance, HR, Manager, Admin, Compliance) clearance
  items; blocked / cleared statuses. Exit is blocked while any clearance item is not `CLEARED`.
- **Knowledge Transfer** — log of KT items with topic, description, transferee, complete flag.
- **Exit Interview** — one record per resignation with rating, would-recommend, structured
  responses (JSON), free-text comments.
- **Relieving + Experience Letters** — exit-document store (uri + reference number + notes) per
  document type: `RELIEVING_LETTER`, `EXPERIENCE_LETTER`, `FNF_STATEMENT`, `PF_STATEMENT`, `OTHER`.
- **Alumni Records** — persistent record for ex-employees (alumni email, LinkedIn, current
  employer, stay-in-touch flag, rehire eligibility).
- **Rehire Eligibility** — captured at exit and on alumni: `YES` / `NO` / `WITH_APPROVAL`.
- **Exit Dashboard, Reports** — status counts + alumni counts by rehire eligibility.

## Data model

Migration `V32__employee_exit_alumni.sql`.

- `resignations` — one open row per employee enforced by partial unique index on
  `(tenant_id, employee_id) WHERE status NOT IN ('WITHDRAWN','CANCELLED')`.
- `exit_clearances` — per-department clearance item, cascade-deleted when the resignation is.
- `knowledge_transfer_items` — KT log entries.
- `exit_interviews` — one interview per resignation (partial unique index).
- `exit_documents` — issued documents per document type.
- `alumni_records` — one alumni record per employee (partial unique index).

## Endpoints

Under `/api/v1/exit/resignations`, `/api/v1/exit` (clearance / KT / interview / documents), and
`/api/v1/exit/alumni`. Every endpoint requires the `X-Tenant-Id` header and one of the `EXIT_*` or
`ALUMNI_MANAGE` authorities.

## Permissions

| Code | Purpose |
| --- | --- |
| `EXIT_READ` | View resignations, clearance, interviews, alumni |
| `EXIT_WRITE` | Create + manage resignations, clearance items, KT items |
| `EXIT_APPROVE` | Accept resignations, start notice, buyout, cancel, complete exit |
| `EXIT_CLEAR` | Record clearance for a department |
| `EXIT_INTERVIEW` | Conduct exit interviews |
| `EXIT_ISSUE_DOC` | Issue relieving / experience letters |
| `ALUMNI_MANAGE` | Manage alumni records |
| `EXIT_ADMIN` | Administer exit module |

All eight are seeded onto `SYSTEM_ADMIN` by the V32 migration.

## Lifecycle

`ResignationLifecyclePolicy` enforces the state machine:

- `SUBMITTED` → `ACCEPTED` | `WITHDRAWN` | `CANCELLED`
- `ACCEPTED` → `IN_NOTICE` | `EXITED` | `CANCELLED`
- `IN_NOTICE` → `EXITED` | `CANCELLED`
- `EXITED`, `WITHDRAWN`, `CANCELLED` are terminal.

`completeExit` is additionally blocked while any clearance item on the resignation is not
`CLEARED`.

## Events

`ExitEvent` on topic `ewos.exit.event` via the kafka-optional publisher pattern. Event types cover
the full lifecycle: `RESIGNATION_SUBMITTED`, `RESIGNATION_ACCEPTED`, `RESIGNATION_WITHDRAWN`,
`RESIGNATION_CANCELLED`, `NOTICE_STARTED`, `BUYOUT_APPLIED`, `CLEARANCE_CREATED`,
`CLEARANCE_UPDATED`, `CLEARANCE_CLEARED`, `CLEARANCE_BLOCKED`, `KT_ITEM_ADDED`,
`KT_ITEM_COMPLETED`, `EXIT_INTERVIEW_RECORDED`, `EXIT_DOCUMENT_ISSUED`, `EMPLOYEE_EXITED`,
`ALUMNI_CREATED`, `ALUMNI_UPDATED`.
