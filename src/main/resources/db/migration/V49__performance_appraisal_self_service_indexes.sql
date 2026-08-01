-- Sprint 24A — supports the new self-service surface (PerformanceSelfService): "my appraisals"
-- (employee-scoped, across cycles) and "pending my review" (manager/reviewer-scoped, filtered by
-- status). None of the existing indexes on appraisals cover an employee/manager/reviewer lookup
-- that isn't also scoped to a single cycle.
CREATE INDEX ix_appraisals_employee
    ON appraisals (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_appraisals_manager_status
    ON appraisals (tenant_id, manager_employee_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_appraisals_reviewer_status
    ON appraisals (tenant_id, reviewer_employee_id, status)
    WHERE deleted_at IS NULL;
