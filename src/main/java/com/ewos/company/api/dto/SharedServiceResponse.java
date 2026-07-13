package com.ewos.company.api.dto;

import com.ewos.company.domain.TeamType;
import java.time.LocalDate;
import java.util.UUID;

public record SharedServiceResponse(
        UUID id,
        UUID companyId,
        TeamType teamType,
        UUID teamRef,
        String teamLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
