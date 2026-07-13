package com.ewos.person.api.dto;

import com.ewos.person.domain.IdentityDocumentKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        description =
                "Add a PAN / Aadhaar / Passport / Driving Licence / Voter ID / Other document")
public record AddIdentityDocumentRequest(
        @NotNull IdentityDocumentKind documentKind,
        @NotBlank @Size(max = 60) String documentNumber,
        @Size(max = 255) String issuedBy,
        LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 500) String documentUrl,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean verified) {}
