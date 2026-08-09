package com.ewos.employee.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** Sprint 27C — minimal org-unit reference embedded in My Team responses (PRD §4.3). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MssOrgUnitRef(UUID id, String name) {}
