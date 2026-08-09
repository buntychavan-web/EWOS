package com.ewos.employee.api.dto;

import com.ewos.employee.domain.CalendarEventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.Map;

/** Sprint 27C — one merged calendar entry (PRD §4.7). {@code metadata} is null for holidays. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalendarEventResponse(
        LocalDate date, CalendarEventType type, String title, Map<String, String> metadata) {}
