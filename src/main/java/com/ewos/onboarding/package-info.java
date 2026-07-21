/**
 * Employee Onboarding module — Talent Volume 5, milestone T5.
 *
 * <p>Owns the post-joining employee experience: onboarding plans, task assignments to managers /
 * buddies / HR / IT, and 30/60/90-day surveys. A plan is auto-created when a T4 pre-boarding
 * checklist reaches {@code JOINED}, so the full hire → offer → pre-board → onboard chain is
 * stitched together in one join query.
 *
 * <p>Layout follows the standard {@code com.ewos.<module>} shape:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, DTOs, mappers.
 *   <li>{@code .application} — use-case services + handoff listener.
 *   <li>{@code .domain} — aggregates, enums, policy, domain events.
 *   <li>{@code .infrastructure} — JPA repositories, Kafka publisher.
 * </ul>
 */
package com.ewos.onboarding;
