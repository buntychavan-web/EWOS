package com.ewos.ats.api.dto;

import com.ewos.ats.domain.RejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectApplicationRequest(
        @NotNull RejectionReason reason, @Size(max = 4000) String notes) {}
