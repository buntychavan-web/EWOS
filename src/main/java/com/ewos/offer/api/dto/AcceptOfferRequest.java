package com.ewos.offer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptOfferRequest(@NotBlank @Size(max = 1024) String candidateSignature) {}
