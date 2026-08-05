package com.ewos.exit.api;

import com.ewos.exit.application.ExitDocumentGenerationService;
import com.ewos.exit.domain.ExitDocumentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exit/resignations/{resignationId}/documents")
@Tag(
        name = "Exit — Document generation",
        description = "On-demand PDF generation from configured templates")
public class ExitDocumentGenerationController {

    private final ExitDocumentGenerationService generation;

    public ExitDocumentGenerationController(ExitDocumentGenerationService generation) {
        this.generation = generation;
    }

    @GetMapping("/{documentType}/pdf")
    @PreAuthorize("hasAuthority('EXIT_ISSUE_DOC')")
    @Operation(summary = "Generate a letter PDF from the effective template for this document type")
    public ResponseEntity<byte[]> generatePdf(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID resignationId,
            @PathVariable ExitDocumentType documentType) {
        byte[] pdf = generation.generate(tenantId, resignationId, documentType);
        String filename = documentType.name().toLowerCase(Locale.ROOT) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
