package com.ewos.reimbursement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.reimbursement.domain.ReimbursementCategory;
import com.ewos.reimbursement.domain.ReimbursementClaim;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 2 — mirrors {@code LeaveRequestRepositoryIntegrationTest}'s tenant-isolation proof
 * exactly: a manager's pending-reimbursement count/list is only ever computed from the tenant it
 * actually belongs to, never leaked across tenants even when a claim row happens to carry the same
 * manager id (a corrupted cross-tenant pointer constructed directly at the repository level).
 */
@Transactional
class ReimbursementClaimRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ReimbursementClaimRepository claims;
    @Autowired ReimbursementCategoryRepository categories;
    @Autowired EmployeeRepository employees;
    @Autowired TenantRepository tenants;

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCode(label + "-" + SEQ.incrementAndGet());
        t.setName(label);
        return tenants.save(t);
    }

    private Employee employee(UUID tenantId, String label, Employee manager) {
        Employee e = new Employee();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployeeNumber(label + "-" + SEQ.incrementAndGet());
        e.setFirstName(label);
        e.setLastName("Report");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setManager(manager);
        return employees.save(e);
    }

    private ReimbursementCategory category(UUID tenantId) {
        ReimbursementCategory c = new ReimbursementCategory();
        c.setTenantId(tenantId);
        c.setCode("RC_IT_" + SEQ.incrementAndGet());
        c.setName("Integration Test Category");
        return categories.save(c);
    }

    private ReimbursementClaim claim(
            UUID tenantId,
            Employee employee,
            ReimbursementCategory category,
            ReimbursementClaimStatus status) {
        ReimbursementClaim c = new ReimbursementClaim();
        c.setTenantId(tenantId);
        c.setCompanyId(COMPANY_ID);
        c.setEmployee(employee);
        c.setClaimNumber("RC-IT-" + SEQ.incrementAndGet());
        c.setCategory(category);
        c.setAmount(new BigDecimal("500.00"));
        c.setCurrency("INR");
        c.setClaimDate(LocalDate.of(2026, 8, 1));
        c.setDescription("Integration test claim");
        c.setStatus(status);
        return claims.save(c);
    }

    @Test
    void countByTenantIdAndStatusAndManagerIdCountsOnlySubmittedClaimsWithinTheTenant() {
        UUID tenantA = tenant("RcCountTenantA").getId();
        ReimbursementCategory cat = category(tenantA);
        Employee managerA = employee(tenantA, "RcCountManagerA", null);
        Employee report1 = employee(tenantA, "RcCountReport1", managerA);
        Employee report2 = employee(tenantA, "RcCountReport2", managerA);
        claim(tenantA, report1, cat, ReimbursementClaimStatus.SUBMITTED);
        claim(tenantA, report2, cat, ReimbursementClaimStatus.SUBMITTED);
        claim(tenantA, report1, cat, ReimbursementClaimStatus.DRAFT);

        long count =
                claims.countByTenantIdAndStatusAndManagerId(
                        tenantA, ReimbursementClaimStatus.SUBMITTED, managerA.getId());

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void
            countByTenantIdAndStatusAndManagerIdNeverCountsAClaimFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("RcCountTenantB1").getId();
        UUID tenantB = tenant("RcCountTenantB2").getId();
        ReimbursementCategory catA = category(tenantA);
        ReimbursementCategory catB = category(tenantB);
        Employee managerA = employee(tenantA, "RcCountManagerB", null);
        Employee legitimateReport = employee(tenantA, "RcCountLegitReport", managerA);
        claim(tenantA, legitimateReport, catA, ReimbursementClaimStatus.SUBMITTED);

        // Corrupted cross-tenant pointer, constructed directly at the repository level, proving the
        // query itself never leaks data across tenants.
        Employee crossTenantReportEmployee =
                employee(tenantB, "RcCountCrossTenantReport", managerA);
        claim(tenantB, crossTenantReportEmployee, catB, ReimbursementClaimStatus.SUBMITTED);

        long count =
                claims.countByTenantIdAndStatusAndManagerId(
                        tenantA, ReimbursementClaimStatus.SUBMITTED, managerA.getId());

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void findAllForEmployeeNeverReturnsAnotherEmployeesClaimEvenWithinTheSameTenant() {
        UUID tenantA = tenant("RcOwnershipTenant").getId();
        ReimbursementCategory cat = category(tenantA);
        Employee employeeA = employee(tenantA, "RcOwnershipEmployeeA", null);
        Employee employeeB = employee(tenantA, "RcOwnershipEmployeeB", null);
        claim(tenantA, employeeA, cat, ReimbursementClaimStatus.DRAFT);
        claim(tenantA, employeeB, cat, ReimbursementClaimStatus.DRAFT);

        var page =
                claims.findAllForEmployee(tenantA, employeeA.getId(), null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getEmployee().getId()).isEqualTo(employeeA.getId());
    }
}
