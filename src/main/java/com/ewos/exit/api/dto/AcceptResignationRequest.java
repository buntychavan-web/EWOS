package com.ewos.exit.api.dto;

import java.time.LocalDate;

public record AcceptResignationRequest(
        LocalDate noticeStartDate, LocalDate noticeEndDate, String notes) {}
