package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Size;

public record UnlinkUserRequest(@Size(max = 500) String reason) {}
