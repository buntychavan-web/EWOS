/**
 * Integration module (Sprint 14.4).
 *
 * <p>Generic Integration Adapter Framework (REST/SFTP/CSV/Excel/File Upload), Integration
 * Monitoring, Business Error Classification, the Operations Dashboard, and Client Go-Live
 * Configuration. Composes entirely against Sprint 14.3's {@code DataExchangeService} public API —
 * no changes to {@code com.ewos.dataexchange}, {@code com.ewos.payroll}, or {@code
 * com.ewos.workflow}.
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mappers.
 *   <li>{@code .application} — use-case orchestration (application services).
 *   <li>{@code .domain} — aggregates, entities, value objects, the adapter port, domain services.
 *   <li>{@code .infrastructure} — JPA repositories and adapter implementations.
 * </ul>
 */
package com.ewos.integration;
