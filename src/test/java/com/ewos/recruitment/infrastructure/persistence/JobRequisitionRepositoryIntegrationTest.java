package com.ewos.recruitment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionStatus;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27B — {@code findAllByTenantIdAndStatusAndHiringManagerId} against the real database,
 * mirroring {@code TimesheetRepositoryIntegrationTest}'s tenant-isolation proof exactly. Unlike
 * Leave/Timesheet/Probation, the "manager" here is the requisition's own {@code hiringManager}
 * field, not derived from an employee's manager chain — but the tenant-isolation shape of the query
 * (and the risk of a corrupted cross-tenant FK) is identical.
 */
@Transactional
class JobRequisitionRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired JobRequisitionRepository requisitions;
    @Autowired JobPositionRepository jobPositions;
    @Autowired EmployeeRepository employees;
    @Autowired TenantRepository tenants;

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCode(label + "-" + SEQ.incrementAndGet());
        t.setName(label);
        return tenants.save(t);
    }

    private Employee employee(UUID tenantId, String label) {
        Employee e = new Employee();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployeeNumber(label + "-" + SEQ.incrementAndGet());
        e.setFirstName(label);
        e.setLastName("Manager");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return employees.save(e);
    }

    private JobPosition jobPosition(UUID tenantId) {
        JobPosition p = new JobPosition();
        p.setTenantId(tenantId);
        p.setCompanyId(COMPANY_ID);
        p.setCode("POS-" + SEQ.incrementAndGet());
        p.setTitle("Engineer");
        p.setEmploymentType(EmploymentType.FULL_TIME);
        return jobPositions.save(p);
    }

    private JobRequisition requisition(
            UUID tenantId, JobPosition position, Employee hiringManager, RequisitionStatus status) {
        JobRequisition r = new JobRequisition();
        r.setTenantId(tenantId);
        r.setCompanyId(COMPANY_ID);
        r.setRequisitionNumber("REQ-" + SEQ.incrementAndGet());
        r.setJobPosition(position);
        r.setTitle(position.getTitle());
        r.setEmploymentType(EmploymentType.FULL_TIME);
        r.setHeadcount(1);
        r.setHiringManager(hiringManager);
        r.setStatus(status);
        return requisitions.save(r);
    }

    @Test
    void returnsOnlyPendingApprovalRequisitionsOfTheGivenHiringManagerWithinTheTenant() {
        UUID tenantA = tenant("ReqTenantA").getId();
        Employee managerA = employee(tenantA, "ManagerA");
        JobPosition position = jobPosition(tenantA);
        JobRequisition pending1 =
                requisition(tenantA, position, managerA, RequisitionStatus.PENDING_APPROVAL);
        JobRequisition pending2 =
                requisition(tenantA, position, managerA, RequisitionStatus.PENDING_APPROVAL);
        requisition(
                tenantA, position, managerA, RequisitionStatus.DRAFT); // not submitted — excluded

        List<UUID> ids =
                requisitions
                        .findAllByTenantIdAndStatusAndHiringManagerId(
                                tenantA,
                                RequisitionStatus.PENDING_APPROVAL,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(JobRequisition::getId)
                        .toList();

        assertThat(ids).containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
    }

    @Test
    void neverReturnsARequisitionFromADifferentTenantEvenIfTheHiringManagerIdMatches() {
        UUID tenantA = tenant("ReqTenantB1").getId();
        UUID tenantB = tenant("ReqTenantB2").getId();
        Employee managerA = employee(tenantA, "ManagerA2");
        JobPosition positionA = jobPosition(tenantA);
        JobRequisition legitimate =
                requisition(tenantA, positionA, managerA, RequisitionStatus.PENDING_APPROVAL);

        // Corrupted cross-tenant pointer, constructed directly at the repository level (bypassing
        // service-layer validation) to prove the query itself, not just the service, never leaks
        // data across tenants — same defense-in-depth rationale as
        // EmployeeRepositoryIntegrationTest/TimesheetRepositoryIntegrationTest.
        JobPosition positionB = jobPosition(tenantB);
        requisition(tenantB, positionB, managerA, RequisitionStatus.PENDING_APPROVAL);

        List<UUID> ids =
                requisitions
                        .findAllByTenantIdAndStatusAndHiringManagerId(
                                tenantA,
                                RequisitionStatus.PENDING_APPROVAL,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(JobRequisition::getId)
                        .toList();

        assertThat(ids).containsExactly(legitimate.getId());
    }
}
