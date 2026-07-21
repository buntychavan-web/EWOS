package com.ewos.offer.api.dto;

import jakarta.validation.constraints.Size;

public record OfferDecisionRequest(@Size(max = 4000) String notes) {}
