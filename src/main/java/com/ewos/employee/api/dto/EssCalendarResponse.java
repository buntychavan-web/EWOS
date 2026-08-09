package com.ewos.employee.api.dto;

import java.util.List;

/** Sprint 27C — {@code GET /api/v1/self-service/calendar} (PRD §4.7), sorted by date ascending. */
public record EssCalendarResponse(List<CalendarEventResponse> events) {}
