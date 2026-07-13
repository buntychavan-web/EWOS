package com.ewos.company.api.dto;

import jakarta.validation.constraints.NotNull;

public record CompanyStatusRequest(@NotNull Boolean active) {}
