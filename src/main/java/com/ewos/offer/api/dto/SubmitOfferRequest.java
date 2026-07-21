package com.ewos.offer.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitOfferRequest(@NotNull UUID workflowDefinitionId) {}
