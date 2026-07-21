package com.ewos.offer.api.dto;

import java.util.UUID;

public record OfferTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        String bodyTemplate,
        String defaultCurrency,
        Integer defaultNoticePeriodDays,
        Integer defaultProbationDays,
        int defaultExpiryDays,
        boolean active,
        long versionNo) {}
