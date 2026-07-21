package com.ewos.offer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeclineOfferRequest(@NotBlank @Size(max = 4000) String reason) {}
