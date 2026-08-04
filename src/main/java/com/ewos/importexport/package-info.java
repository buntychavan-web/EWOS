/**
 * Import/Export module.
 *
 * <p>Sprint 24E — a shared, module-agnostic import-job audit trail ({@link
 * com.ewos.importexport.domain.ImportJob}, {@link com.ewos.importexport.domain.ImportJobError})
 * used by the Goals/Competency/Development-Plan bulk-import endpoints. Every module under {@code
 * com.ewos.<module>} follows the same internal layout:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mappers.
 *   <li>{@code .application} — use-case orchestration (application services).
 *   <li>{@code .domain} — aggregates, entities, value objects, domain services.
 *   <li>{@code .infrastructure} — JPA repositories, external adapters.
 * </ul>
 */
package com.ewos.importexport;
