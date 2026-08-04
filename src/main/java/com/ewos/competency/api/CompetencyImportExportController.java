package com.ewos.competency.api;

import com.ewos.competency.application.CompetencyImportExportService;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.shared.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Sprint 24E — CSV export/template-download/bulk-import for employee competency levels. */
@RestController
@RequestMapping("/api/v1/competency-matrix/import-export")
@Tag(
        name = "Competency Import/Export",
        description = "Bulk CSV import and export for employee competency levels")
public class CompetencyImportExportController {

    private final CompetencyImportExportService service;

    public CompetencyImportExportController(CompetencyImportExportService service) {
        this.service = service;
    }

    @GetMapping(value = "/template.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('COMPETENCY_WRITE')")
    @Operation(summary = "Download the CSV template for bulk employee-competency import")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employee-competencies-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.templateCsv());
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Export all of a company's employee competency levels as CSV")
    public ResponseEntity<String> export(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employee-competencies-" + companyId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.exportCsv(tenantId, companyId));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('COMPETENCY_WRITE')")
    @Operation(
            summary = "Bulk-import employee competency levels from CSV",
            description =
                    "Partial success: valid rows are applied even if others fail. Response carries "
                            + "the per-row validation/error report; the import job itself is also "
                            + "persisted as an audit record.")
    public ImportResultResponse importEmployeeCompetencies(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestPart("file") MultipartFile file) {
        return service.importCsv(tenantId, companyId, file.getOriginalFilename(), readUtf8(file));
    }

    private static String readUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded file", e);
        }
    }
}
