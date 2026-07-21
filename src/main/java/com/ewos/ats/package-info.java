/**
 * Applicant Tracking System (ATS) — Talent Volume 5, milestone T2.
 *
 * <p>Owns candidate identity, resumes / documents, tags / notes / timeline / communications, and
 * job applications. Applications reference {@link com.ewos.recruitment.domain.JobRequisition
 * requisitions} from the T1 recruitment module; each application drives a hiring pipeline through
 * the workflow engine (subject-type {@code ats.application}).
 *
 * <p>Layout follows the standard {@code com.ewos.<module>} shape:
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, DTOs, mappers.
 *   <li>{@code .application} — use-case services.
 *   <li>{@code .domain} — aggregates, enums, policies, domain events.
 *   <li>{@code .infrastructure} — JPA repositories, resume-parser plug-in, Kafka publisher.
 * </ul>
 */
package com.ewos.ats;
