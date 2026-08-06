package com.ewos.exit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 26A P0-1: a resignation must stay readable — including through {@code getEmployee()},
 * which every caller in the exit module already null-checks defensively — after the linked employee
 * is soft-deleted. Before the {@code @NotFound(IGNORE)} fix, Hibernate threw {@code
 * EntityNotFoundException} the moment the association was touched, because {@link Employee} is
 * itself soft-deleted ({@code @SQLRestriction("deleted_at IS NULL")}) and the association was
 * {@code optional = false} with no not-found handling.
 */
@Transactional
class ResignationEmployeeSoftDeleteIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired EmployeeRepository employees;
    @Autowired ResignationRepository resignations;
    @Autowired EntityManager entityManager;

    @Test
    void resignationStaysReadableAfterItsEmployeeIsSoftDeleted() {
        Employee employee = new Employee();
        employee.setTenantId(TENANT_ID);
        employee.setCompanyId(COMPANY_ID);
        employee.setEmployeeNumber("SOFTDEL-" + SEQ.incrementAndGet());
        employee.setFirstName("Jordan");
        employee.setLastName("Rivera");
        employee.setWorkEmail("softdel" + SEQ.get() + "@example.com");
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employees.save(employee);

        Resignation resignation = new Resignation();
        resignation.setTenantId(TENANT_ID);
        resignation.setCompanyId(COMPANY_ID);
        resignation.setEmployee(employee);
        resignation.setResignationType(ResignationType.SELF_RESIGNATION);
        resignation.setSubmittedAt(Instant.now());
        resignation.setIntendedLastDay(LocalDate.now().plusDays(30));
        resignation.setNoticePeriodDays(30);
        resignation.setStatus(ResignationStatus.SUBMITTED);
        resignation = resignations.save(resignation);
        UUID resignationId = resignation.getId();

        employees.delete(employee);
        entityManager.flush();
        entityManager.clear();

        Resignation reloaded = resignations.findById(resignationId).orElseThrow();

        assertThatCode(reloaded::getEmployee).doesNotThrowAnyException();
        assertThat(reloaded.getEmployee()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(ResignationStatus.SUBMITTED);
    }
}
