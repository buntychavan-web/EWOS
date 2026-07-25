/**
 * Data Exchange module (Sprint 14.3).
 *
 * <p>Tracks the operational queue of exchanging payroll & HR business data with an external system
 * — a future HRMS connector, not built here. Records are created automatically by listeners on the
 * platform's existing domain events, or explicitly via the REST API.
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mappers.
 *   <li>{@code .application} — use-case orchestration (application services).
 *   <li>{@code .domain} — aggregates, entities, value objects, domain services.
 *   <li>{@code .infrastructure} — JPA repositories, external adapters.
 * </ul>
 */
package com.ewos.dataexchange;
