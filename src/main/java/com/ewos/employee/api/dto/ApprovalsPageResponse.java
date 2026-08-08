package com.ewos.employee.api.dto;

import java.util.List;

/**
 * Sprint 27B — cursor-paginated page of {@link ApprovalItemResponse}. {@code nextCursor} is {@code
 * null} once the caller has reached the end of the merged, stably-ordered (pending-since ascending)
 * result set; pass it back as the {@code cursor} query parameter to fetch the next page.
 */
public record ApprovalsPageResponse(
        List<ApprovalItemResponse> items, String nextCursor, boolean actingForDelegator) {}
