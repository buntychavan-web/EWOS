package com.ewos.importexport.application;

import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.api.dto.ImportRowError;
import com.ewos.importexport.domain.ImportJob;
import com.ewos.importexport.domain.ImportJobError;
import com.ewos.importexport.infrastructure.persistence.ImportJobErrorRepository;
import com.ewos.importexport.infrastructure.persistence.ImportJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24E — records one {@link ImportJob} (+ its {@link ImportJobError} rows) per bulk-import
 * run. Deliberately its own transaction, called only after every row has already been individually
 * processed (each row succeeding/failing in its own transaction via the target module's normal
 * {@code @Transactional} service methods) — this class never touches the rows being imported
 * itself, only the audit record of what happened to them.
 */
@Service
public class ImportJobRecorder {

    private final ImportJobRepository jobs;
    private final ImportJobErrorRepository errors;

    public ImportJobRecorder(ImportJobRepository jobs, ImportJobErrorRepository errors) {
        this.jobs = jobs;
        this.errors = errors;
    }

    @Transactional
    public ImportResultResponse record(
            UUID tenantId,
            UUID companyId,
            String module,
            String fileName,
            Instant startedAt,
            int totalRows,
            List<ImportRowError> rowErrors) {
        ImportJob job = new ImportJob();
        job.setTenantId(tenantId);
        job.setCompanyId(companyId);
        job.setModule(module);
        job.setFileName(fileName);
        job.setTotalRows(totalRows);
        job.setFailureCount(rowErrors.size());
        job.setSuccessCount(totalRows - rowErrors.size());
        job.setStatus("COMPLETED");
        job.setStartedAt(startedAt);
        job.setCompletedAt(Instant.now());
        job = jobs.save(job);

        for (ImportRowError e : rowErrors) {
            ImportJobError row = new ImportJobError();
            row.setImportJob(job);
            row.setRowNumber(e.rowNumber());
            row.setRowData(truncate(e.rowData(), 2000));
            row.setErrorMessage(truncate(e.errorMessage(), 1000));
            errors.save(row);
        }

        return new ImportResultResponse(
                job.getId(), totalRows, job.getSuccessCount(), job.getFailureCount(), rowErrors);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
