package com.ewos.employee.api.dto;

import java.util.List;

/** Sprint 27C — cursor-paginated page of {@link MssTeamMemberResponse} (PRD §4.3). */
public record MssTeamPageResponse(List<MssTeamMemberResponse> items, String nextCursor) {}
