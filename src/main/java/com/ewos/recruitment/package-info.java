/**
 * Recruitment module — Talent Volume 5, milestone T1.
 *
 * <p>Owns the hiring pipeline: {@link com.ewos.recruitment.domain.JobPosition long-lived positions}
 * and {@link com.ewos.recruitment.domain.JobRequisition requisitions} raised against them.
 * Requisitions route through the workflow engine (subject-type {@code recruitment.requisition}) for
 * approval. Lifecycle events are published on {@code ewos.recruitment.event} for downstream ATS,
 * interview, offer, and onboarding milestones.
 *
 * <p>Layout follows the standard {@code com.ewos.<module>} shape:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mappers.
 *   <li>{@code .application} — use-case services.
 *   <li>{@code .domain} — aggregates, enums, policies, domain events.
 *   <li>{@code .infrastructure} — JPA repositories, Kafka publisher.
 * </ul>
 */
package com.ewos.recruitment;
