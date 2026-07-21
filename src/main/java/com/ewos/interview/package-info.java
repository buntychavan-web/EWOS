/**
 * Interview Management module — Talent Volume 5, milestone T3.
 *
 * <p>Owns interview templates, scheduled rounds, panel composition, scorecards, and candidate
 * feedback. Rounds attach to {@link com.ewos.ats.domain.JobApplication ATS applications} from T2
 * and drive per-round decisions that feed the application pipeline.
 *
 * <p>Layout follows the standard {@code com.ewos.<module>} shape:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, DTOs, mappers.
 *   <li>{@code .application} — use-case services.
 *   <li>{@code .domain} — aggregates, enums, policies, domain events.
 *   <li>{@code .infrastructure} — JPA repositories, calendar/notifier plug-ins, Kafka publisher.
 * </ul>
 */
package com.ewos.interview;
