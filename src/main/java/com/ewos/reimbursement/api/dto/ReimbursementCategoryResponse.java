package com.ewos.reimbursement.api.dto;

import java.util.UUID;

public record ReimbursementCategoryResponse(
        UUID id, String code, String name, String description) {}
