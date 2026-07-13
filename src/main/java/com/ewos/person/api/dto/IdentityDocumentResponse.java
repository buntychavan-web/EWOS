package com.ewos.person.api.dto;

import com.ewos.person.domain.IdentityDocumentKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Identity document for a Person")
public record IdentityDocumentResponse(
        UUID id,
        UUID personId,
        IdentityDocumentKind documentKind,
        String documentNumber,
        String issuedBy,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String documentUrl,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean verified,
        long version) {}
