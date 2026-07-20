# Workflow module

**Package:** `com.ewos.workflow`
**Introduced:** WP-006
**Depends on:** `shared`, `identity`
**Consumed by (future):** `attendance`, `leave`, `payroll`, `recruitment`, `talent`, `governance`

## Purpose

Metadata-driven state-machine orchestration engine. Every downstream module that needs an
approval or lifecycle flow (leave request approval, timesheet approval, hire approval, expense
approval, ...) registers a workflow definition here and drives its instances instead of
hard-coding the flow in code.

## Domain model

Six aggregates / entities:

### `WorkflowDefinition`
Versioned per-tenant. Once published it is immutable; new revisions bump `definitionVersion`.
Holds an ordered list of `WorkflowState` and `WorkflowTransition` children.

### `WorkflowState`
Node in the graph. Exactly one state per definition has `isInitial=true` (DB partial-unique
index enforces this). Terminal states carry `isTerminal=true` and cannot have outgoing
transitions.

### `WorkflowTransition`
Directed edge. Identified by `(fromState, actionCode)` — DB unique index guarantees at most one.
Optional `requiredRole`, `guardExpression`, and an `auto` flag that the engine follows without
requiring a human task.

### `WorkflowInstance`
A specific run bound to a subject entity (`subjectType` + `subjectId`, e.g. `("leave_request",
uuid)`). Multi-tenant + multi-company. Optional `correlationKey` — unique per tenant — to
prevent duplicate starts.

### `WorkflowTask`
Human task emitted when a state needs an actor decision. Assigned to a `USER`, `EMPLOYEE`,
`ROLE`, or `SYSTEM` actor. Completing a task drives the instance forward via
`WorkflowInstanceService.advance`.

### `WorkflowHistory`
Append-only audit row per state transition. Never soft-deleted, never versioned.

## Domain policy — `WorkflowTransitionPolicy`

Framework-neutral rule enforcer:

- Exactly one initial state, at least one terminal state.
- No transitions from a terminal state.
- Resolve `(fromState, actionCode)` → transition or 409.
- Auto transitions found in one call for automatic advance.
- Instance must be `RUNNING`; task must be `OPEN` or `CLAIMED`.

## Automatic advance

After every explicit advance, the engine follows any `auto=true` transition from the new state.
A bounded 32-hop safety loop prevents runaway loops in bad definitions — hitting the ceiling
transitions the instance to `ERROR` and emits `INSTANCE_ERRORED`.

## Public API — base `/api/v1/workflow`

### Definitions

| Method | Path | Auth |
|---|---|---|
| `POST` | `/definitions` | `WF_WRITE` |
| `GET` | `/definitions[/{id}]` | `WF_READ` |
| `POST` | `/definitions/{id}/active?active=true|false` | `WF_ADMIN` |
| `DELETE` | `/definitions/{id}` | `WF_ADMIN` |

Fetch-by-id is Redis-cached (`workflow.definition::{tenant}:{id}`).

### Instances

| Method | Path | Auth |
|---|---|---|
| `POST` | `/instances` | `WF_WRITE` |
| `GET` | `/instances/{id}` | `WF_READ` |
| `GET` | `/instances?subjectType=&subjectId=` | `WF_READ` |
| `GET` | `/instances/{id}/history` | `WF_READ` |
| `POST` | `/instances/{id}/cancel` | `WF_ADMIN` |

### Tasks

| Method | Path | Auth |
|---|---|---|
| `POST` | `/tasks/for-instance/{instanceId}` | `WF_WRITE` |
| `GET` | `/tasks/{id}` | `WF_READ` |
| `POST` | `/tasks/{id}/claim` | `WF_ACT` |
| `POST` | `/tasks/{id}/complete` | `WF_ACT` |
| `POST` | `/tasks/{id}/cancel` | `WF_ADMIN` |
| `GET` | `/tasks/mine?actorId=` | `WF_ACT` |
| `GET` | `/tasks/by-role?roleCode=` | `WF_ACT` |
| `GET` | `/tasks/of-instance/{instanceId}` | `WF_READ` |

All read/write endpoints require an `X-Tenant-Id` header.

## Persistence — Flyway `V11__workflow_engine.sql`

- 6 tables: `workflow_definitions`, `workflow_states`, `workflow_transitions`,
  `workflow_instances`, `workflow_tasks`, `workflow_history`.
- `tenant_id UUID NOT NULL` on definitions, instances, and tasks (no FK yet).
- CHECK constraints for instance status, task status/actor combos, `no-self-loop unless auto`.
- Partial unique index on `(definition_id) WHERE is_initial = TRUE` enforces exactly-one-initial.
- Partial unique index on `(definition_id, from_state_id, LOWER(action_code))` enforces
  deterministic action resolution.
- Seeds `WF_READ`, `WF_WRITE`, `WF_ADMIN`, `WF_ACT` permissions; grants to `SYSTEM_ADMIN`.

## Events

Every state entry / transition / task change fires a `WorkflowEvent` on Spring's
`ApplicationEventPublisher`. `WorkflowEventPublisher` forwards to Kafka topic
`ewos.workflow.event` on `AFTER_COMMIT`.

Event types: `DEFINITION_PUBLISHED`, `INSTANCE_STARTED`, `STATE_ENTERED`, `TASK_ASSIGNED`,
`TASK_COMPLETED`, `TASK_ESCALATED`, `INSTANCE_COMPLETED`, `INSTANCE_CANCELLED`,
`INSTANCE_ERRORED`.

## Caching

`WorkflowDefinitionService.getById` is Redis-cached under
`workflow.definition::{tenantId}:{id}` with the platform-default 10-min TTL. Every write on a
definition or its active-flag change evicts all entries.

## Consuming the module (downstream pattern)

Leave module (WP-008) will use it roughly like this:

```java
// On leave request creation
UUID definitionId = workflowDefinitions.findLatestActive(tenantId, "leave.approval");
WorkflowInstanceResponse instance = workflowInstances.start(new StartInstanceRequest(
        tenantId, companyId, definitionId, "leave_request",
        leaveRequestId, "leave-" + leaveRequestId));
workflowTasks.assign(tenantId, instance.id(), new AssignTaskRequest(
        WorkflowActorType.EMPLOYEE, managerEmployeeId, null, "APPROVE",
        Instant.now().plus(Duration.ofDays(2)), null));

// Manager approves
workflowTasks.complete(tenantId, taskId, new CompleteTaskRequest("APPROVE", "OK", null));
// Instance auto-advances through any auto transitions; emits STATE_ENTERED and
// INSTANCE_COMPLETED events; Leave module listens on the Kafka topic and updates
// the leave request accordingly.
```

## Testing

- Unit: `WorkflowTransitionPolicyTest` (10), `WorkflowMapperTest` (3),
  `WorkflowInstanceServiceTest` (6). Total 19.
- Integration: deferred to CI (Testcontainers).

## Deferred

- **Guard expression evaluator** — column is present on transitions and passed through the
  service, but no SpEL runner yet. Transitions with a non-null `guardExpression` succeed
  unconditionally. Wire an evaluator when the first real guarded transition ships.
- **SLA breach scheduler** — `sla_hours` and `due_at` are captured; a periodic job to emit
  `TASK_ESCALATED` on breach is a follow-up work package.
- **Bulk task queries by employee** — currently by actorId or roleCode. Multi-tenant search
  API will land with the Analytics module.
- **Task delegation** — user picks another user to receive an assigned task. Add when the
  first customer needs it.
