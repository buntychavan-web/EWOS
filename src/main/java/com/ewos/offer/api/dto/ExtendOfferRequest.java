package com.ewos.offer.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ExtendOfferRequest(@NotNull Instant expiresAt) {}
