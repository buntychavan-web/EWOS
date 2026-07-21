package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateScheduledReportRequest;
import com.ewos.payroll.api.dto.ScheduledReportResponse;
import com.ewos.payroll.domain.ScheduledReport;
import com.ewos.payroll.domain.ScheduledReportFormat;
import com.ewos.payroll.infrastructure.persistence.ScheduledReportRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-only service for scheduled-report declarations. Dispatch (cron evaluation +
 * generation + delivery) is handled by an operator-supplied plugin — this service only stores the
 * intent and last-run marker so the plugin can pick it up.
 */
@Service
@Transactional
public class ScheduledReportService {

    private final ScheduledReportRepository repository;

    public ScheduledReportService(ScheduledReportRepository repository) {
        this.repository = repository;
    }

    public ScheduledReportResponse create(CreateScheduledReportRequest r) {
        ScheduledReport s = new ScheduledReport();
        s.setTenantId(r.tenantId());
        s.setCompanyId(r.companyId());
        s.setReportCode(r.reportCode());
        s.setName(r.name());
        s.setCronExpression(r.cronExpression());
        s.setFormat(r.format() != null ? r.format() : ScheduledReportFormat.CSV);
        s.setParametersJson(PayrollMapper.writeIdentifiers(r.parameters()));
        s.setRecipients(r.recipients());
        if (r.active() != null) {
            s.setActive(r.active());
        }
        return toResponse(repository.save(s));
    }

    public void deactivate(UUID tenantId, UUID id) {
        ScheduledReport s = require(tenantId, id);
        s.setActive(false);
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public ScheduledReportResponse getById(UUID tenantId, UUID id) {
        return toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ScheduledReportResponse> list(UUID tenantId, UUID companyId) {
        return repository.findAllByTenantIdAndCompanyIdOrderByNameAsc(tenantId, companyId).stream()
                .map(ScheduledReportService::toResponse)
                .toList();
    }

    private ScheduledReport require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Scheduled report not found"));
    }

    static ScheduledReportResponse toResponse(ScheduledReport s) {
        return new ScheduledReportResponse(
                s.getId(),
                s.getTenantId(),
                s.getCompanyId(),
                s.getReportCode(),
                s.getName(),
                s.getCronExpression(),
                s.getFormat(),
                s.getParametersJson(),
                s.getRecipients(),
                s.isActive(),
                s.getLastRunAt(),
                s.getLastRunStatus(),
                s.getLastRunMessage(),
                s.getVersionNo());
    }
}
