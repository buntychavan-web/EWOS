package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.Size;

public record DecideTimesheetRequest(@Size(max = 2048) String reason) {}
