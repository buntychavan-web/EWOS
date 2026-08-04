package com.ewos.performance.domain.events;

import java.util.UUID;

/**
 * Internal kickoff signal — not a {@link PerformanceEvent}, never leaves the process, and carries
 * no audit meaning of its own (the {@code appraisal_cycle_launch_batches} row is the audit record).
 * Published by {@code AppraisalCycleLaunchService.launch()} inside its transaction and consumed
 * after commit by {@code AppraisalCycleLaunchRunner}, so the async worker never races the
 * transaction that created the batch row it's about to query.
 */
public record AppraisalCycleLaunchRequested(UUID tenantId, UUID batchId) {}
