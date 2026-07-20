package com.ewos.leave.api.dto;

import jakarta.validation.constraints.Size;

public record DecideLeaveRequestRequest(@Size(max = 2048) String reason) {}
