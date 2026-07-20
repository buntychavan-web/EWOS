package com.ewos.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StateResponse(
        UUID id,
        String code,
        String name,
        boolean initial,
        boolean terminal,
        int sortOrder,
        Integer slaHours) {}
