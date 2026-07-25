/**
 * Tenancy module — Sprint 14.1 Outsourced Payroll Foundation.
 *
 * <p>Owns the {@code Tenant -> Client -> Company} hierarchy, the Service Catalogue, the Payroll
 * Service Provider registry, and Client Assignments (the Chinese Wall enforcement table). See
 * {@code SPRINT_14_TECHNICAL_DESIGN_v3.md} for the approved design.
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mapper.
 *   <li>{@code .application} — use-case orchestration (application services), including {@link
 *       com.ewos.tenancy.application.ClientAccessGuard}, the Chinese Wall check.
 *   <li>{@code .domain} — aggregates, entities, value objects.
 *   <li>{@code .infrastructure} — JPA repositories.
 * </ul>
 */
package com.ewos.tenancy;
