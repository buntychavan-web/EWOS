package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalLifecyclePolicy;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.infrastructure.persistence.AppraisalRatingRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 24A: {@code PERF_APPRAISE_SELF}/{@code _MANAGER}/{@code _REVIEWER} used to be flat
 * platform permissions with no server-side check that the caller is the appraisal's own employee,
 * manager, or reviewer. These tests exercise {@code requireOwnershipUnlessAdmin} directly through
 * {@code submitSelf}/{@code submitManager}/{@code submitReviewer} (mirroring {@code
 * LeaveRequestServiceTest}'s coverage of the equivalent Sprint 4 fix), plus the new self-service
 * query methods ({@code forEmployee}, {@code forParticipant}, {@code pendingForManager}, {@code
 * pendingForReviewer}).
 */
@ExtendWith(MockitoExtension.class)
class AppraisalServiceTest {

    @Mock AppraisalRepository appraisals;
    @Mock AppraisalRatingRepository ratings;
    @Mock EmployeeRepository employees;
    @Mock PerformanceCycleService cycles;
    @Mock AppraisalTemplateService templates;
    @Mock WorkflowInstanceService workflow;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private AppraisalService service;
    private UUID tenantId;
    private UUID actorUserId;
    private UUID employeeId;
    private UUID managerId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        service =
                new AppraisalService(
                        appraisals,
                        ratings,
                        employees,
                        cycles,
                        templates,
                        new AppraisalLifecyclePolicy(),
                        workflow,
                        new PerformanceMapper(),
                        events,
                        guard);
        tenantId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorUserId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void grantPerfAdmin() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorUserId.toString(),
                                null,
                                List.of(new SimpleGrantedAuthority("PERF_ADMIN"))));
    }

    private static Employee employee(UUID id) {
        Employee e = new Employee();
        e.setId(id);
        return e;
    }

    private Appraisal appraisal(AppraisalStatus status) {
        AppraisalTemplate template = new AppraisalTemplate();
        template.setId(UUID.randomUUID());
        template.setRatingScaleMin(1);
        template.setRatingScaleMax(5);
        template.setActive(true);

        Appraisal a = new Appraisal();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setCompanyId(UUID.randomUUID());
        a.setTemplate(template);
        a.setEmployee(employee(employeeId));
        a.setManagerEmployee(employee(managerId));
        a.setReviewerEmployee(employee(reviewerId));
        a.setStatus(status);
        return a;
    }

    private void stubFound(Appraisal a) {
        when(appraisals.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
    }

    // -- submitSelf ownership -------------------------------------------------

    @Test
    void submitSelfRejectsWhenActorIsNotTheAppraisee() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_SELF);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.submitSelf(
                                        tenantId,
                                        a.getId(),
                                        new SelfAssessmentRequest(BigDecimal.valueOf(4), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(ratings, never()).save(any());
    }

    @Test
    void submitSelfSucceedsWhenActorIsTheAppraisee() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_SELF);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(employee(employeeId)));

        service.submitSelf(
                tenantId, a.getId(), new SelfAssessmentRequest(BigDecimal.valueOf(4), null));

        assertThat(a.getStatus()).isEqualTo(AppraisalStatus.PENDING_MANAGER);
    }

    @Test
    void submitSelfSucceedsForPerfAdminRegardlessOfIdentity() {
        grantPerfAdmin();
        Appraisal a = appraisal(AppraisalStatus.PENDING_SELF);
        stubFound(a);

        service.submitSelf(
                tenantId, a.getId(), new SelfAssessmentRequest(BigDecimal.valueOf(4), null));

        assertThat(a.getStatus()).isEqualTo(AppraisalStatus.PENDING_MANAGER);
        verify(employees, never()).findAllByUserIdAndTenantId(any(), any());
    }

    // -- submitManager ownership ----------------------------------------------

    @Test
    void submitManagerRejectsWhenActorIsNotTheAppraisalsManager() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_MANAGER);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.submitManager(
                                        tenantId,
                                        a.getId(),
                                        new ManagerAssessmentRequest(BigDecimal.valueOf(4), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitManagerSucceedsWhenActorIsTheAppraisalsManager() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_MANAGER);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(employee(managerId)));

        service.submitManager(
                tenantId, a.getId(), new ManagerAssessmentRequest(BigDecimal.valueOf(4), null));

        assertThat(a.getStatus()).isEqualTo(AppraisalStatus.PENDING_REVIEWER);
    }

    @Test
    void submitManagerRejectsWhenAppraisalHasNoManagerOnRecord() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_MANAGER);
        a.setManagerEmployee(null);
        stubFound(a);

        assertThatThrownBy(
                        () ->
                                service.submitManager(
                                        tenantId,
                                        a.getId(),
                                        new ManagerAssessmentRequest(BigDecimal.valueOf(4), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -- submitReviewer ownership ----------------------------------------------

    @Test
    void submitReviewerRejectsWhenActorIsNotTheAppraisalsReviewer() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_REVIEWER);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.submitReviewer(
                                        tenantId,
                                        a.getId(),
                                        new ReviewerAssessmentRequest(BigDecimal.valueOf(4), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitReviewerSucceedsWhenActorIsTheAppraisalsReviewer() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_REVIEWER);
        stubFound(a);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(employee(reviewerId)));

        service.submitReviewer(
                tenantId, a.getId(), new ReviewerAssessmentRequest(BigDecimal.valueOf(4), null));

        assertThat(a.getStatus()).isEqualTo(AppraisalStatus.CALIBRATION);
    }

    // -- self-service reads -----------------------------------------------------

    @Test
    void forEmployeeGuardsUsingTheEmployeesOwnCompany() {
        UUID companyId = UUID.randomUUID();
        Employee e = employee(employeeId);
        e.setCompanyId(companyId);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(e));
        when(appraisals.findAllByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId))
                .thenReturn(List.of());

        service.forEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompany(companyId, employeeId);
    }

    @Test
    void forParticipantReturns404ForNonParticipant() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_SELF);
        stubFound(a);
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.forParticipant(tenantId, a.getId(), stranger))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forParticipantSucceedsForTheEmployeeManagerAndReviewer() {
        Appraisal a = appraisal(AppraisalStatus.PENDING_SELF);
        stubFound(a);

        assertThat(service.forParticipant(tenantId, a.getId(), employeeId)).isNotNull();
        assertThat(service.forParticipant(tenantId, a.getId(), managerId)).isNotNull();
        assertThat(service.forParticipant(tenantId, a.getId(), reviewerId)).isNotNull();
    }

    @Test
    void pendingForManagerDelegatesToRepositoryScopedToCaller() {
        service.pendingForManager(tenantId, managerId);

        verify(appraisals)
                .findAllByTenantIdAndManagerEmployeeIdAndStatus(
                        tenantId, managerId, AppraisalStatus.PENDING_MANAGER);
    }

    @Test
    void pendingForReviewerDelegatesToRepositoryScopedToCaller() {
        service.pendingForReviewer(tenantId, reviewerId);

        verify(appraisals)
                .findAllByTenantIdAndReviewerEmployeeIdAndStatus(
                        tenantId, reviewerId, AppraisalStatus.PENDING_REVIEWER);
    }
}
