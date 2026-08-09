package com.ewos.employee.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Sprint 27C — ESS Dashboard leave card (PRD §4.1). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EssLeaveSummaryResponse(
        BigDecimal balanceDays, long pendingRequests, LocalDate nextApprovedLeaveDate) {}
