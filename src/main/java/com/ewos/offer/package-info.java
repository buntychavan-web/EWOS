/**
 * Offer Management & Pre-Boarding module — Talent Volume 5, milestone T4.
 *
 * <p>Owns offer letters, versioning, negotiation, approval workflow, digital acceptance, and the
 * pre-joining checklist that produces a joining confirmation. Reads {@link
 * com.ewos.ats.domain.JobApplication ATS applications} and {@link com.ewos.ats.domain.Candidate
 * candidates} from T2; the interview {@code ROUND_DECIDED / PROCEED} event from T3 can initiate an
 * offer. On joining confirmation, hands off to the Employee master.
 *
 * <p>Layout follows the standard {@code com.ewos.<module>} shape:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, DTOs, mappers.
 *   <li>{@code .application} — use-case services.
 *   <li>{@code .domain} — aggregates, enums, policies, domain events, pluggable frameworks (BGV /
 *       medical check / employee-id generation / notifier).
 *   <li>{@code .infrastructure} — JPA repositories, no-op default framework bindings, Kafka
 *       publisher.
 * </ul>
 */
package com.ewos.offer;
