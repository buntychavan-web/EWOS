package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Optional<PayrollRun> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * True if a REGULAR run already exists for this period that hasn't FAILED (Codex CTO audit
     * P0-4). Backed by a DB-level partial unique index for the race-condition case; this query is
     * the pre-check that turns the race into a clean {@code 409} instead of a raw constraint
     * violation for the common sequential case.
     */
    @Query(
            "select count(r) > 0 from PayrollRun r where r.tenantId = :tenantId and"
                    + " r.payrollPeriod.id = :periodId and r.runType ="
                    + " com.ewos.payroll.domain.PayrollRunType.REGULAR and r.status <>"
                    + " com.ewos.payroll.domain.PayrollRunStatus.FAILED")
    boolean existsActiveRegularRunForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    /**
     * The FROZEN REGULAR run for this period, if one exists (Codex CTO audit P0-6; Sprint 24L item
     * 2 reopen framework). Once the period's regular run is frozen the cycle is treated as fully
     * closed to further payroll activity — a same-period supplementary run is refused unless an
     * active {@link com.ewos.payroll.domain.PayrollRunReopenAuthorization} exists for this exact
     * run.
     */
    @Query(
            "select r from PayrollRun r where r.tenantId = :tenantId and"
                    + " r.payrollPeriod.id = :periodId and r.runType ="
                    + " com.ewos.payroll.domain.PayrollRunType.REGULAR and r.status ="
                    + " com.ewos.payroll.domain.PayrollRunStatus.FROZEN")
    Optional<PayrollRun> findFrozenRegularRunForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    /**
     * True if any run for this period is still mid-lifecycle — not yet FINALIZED/FROZEN and not
     * FAILED (Codex CTO audit P0-5). {@link
     * com.ewos.payroll.application.PayrollPeriodService#close} must refuse to close while this is
     * true, or a period can be marked CLOSED while a run against it is still
     * PENDING/PROCESSING/COMPLETED-but-not-finalized.
     */
    @Query(
            "select count(r) > 0 from PayrollRun r where r.tenantId = :tenantId and"
                    + " r.payrollPeriod.id = :periodId and r.status not in"
                    + " (com.ewos.payroll.domain.PayrollRunStatus.FINALIZED,"
                    + " com.ewos.payroll.domain.PayrollRunStatus.FROZEN,"
                    + " com.ewos.payroll.domain.PayrollRunStatus.FAILED)")
    boolean existsNonTerminalRunForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    @Query(
            "select r from PayrollRun r where r.tenantId = :tenantId and r.payrollPeriod.id ="
                    + " :periodId order by r.createdAt desc")
    List<PayrollRun> findAllForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    List<PayrollRun> findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId, PayrollRunStatus status);

    List<PayrollRun> findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId);

    List<PayrollRun> findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(
            UUID tenantId, List<UUID> companyIds);
}
