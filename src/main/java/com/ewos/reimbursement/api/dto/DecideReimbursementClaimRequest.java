package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Shared approve/reject payload, mirrors {@code DecideLeaveRequestRequest} — {@code reason} is
 * optional at the DTO level; the service enforces it is non-blank specifically on rejection.
 */
public record DecideReimbursementClaimRequest(@Size(max = 2000) String reason) {}
