package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Result of a duplicate-detection scan")
public record DuplicateCheckResponse(List<DuplicateMatch> matches) {}
