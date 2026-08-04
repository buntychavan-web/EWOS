package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.performance.api.dto.AppraisalCycleLaunchBatchResponse;
import com.ewos.performance.api.dto.LaunchAppraisalCycleRequest;
import com.ewos.performance.domain.AppraisalCycleLaunchBatch;
import com.ewos.performance.domain.AppraisalCycleLaunchBatchStatus;
import com.ewos.performance.domain.AppraisalLifecyclePolicy;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.PerformanceCycleStatus;
import com.ewos.performance.domain.events.AppraisalCycleLaunchRequested;
import com.ewos.performance.infrastructure.persistence.AppraisalCycleLaunchBatchRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AppraisalCycleLaunchServiceTest {

    @Mock AppraisalCycleLaunchBatchRepository batches;
    @Mock PerformanceCycleService cycles;
    @Mock AppraisalTemplateService templates;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private AppraisalCycleLaunchService service;
    private UUID tenantId;
    private UUID companyId;
    private UUID cycleId;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        service =
                new AppraisalCycleLaunchService(
                        batches, cycles, templates, new AppraisalLifecyclePolicy(), events, guard);
        tenantId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        cycleId = UUID.randomUUID();
        templateId = UUID.randomUUID();
    }

    private PerformanceCycle openCycle() {
        PerformanceCycle c = new PerformanceCycle();
        c.setId(cycleId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        c.setStatus(PerformanceCycleStatus.OPEN);
        return c;
    }

    private AppraisalTemplate activeTemplate() {
        AppraisalTemplate t = new AppraisalTemplate();
        t.setId(templateId);
        t.setRatingScaleMin(1);
        t.setRatingScaleMax(5);
        t.setActive(true);
        return t;
    }

    @Test
    void launchRejectsWhenAnotherBatchIsAlreadyPendingOrRunningForTheCycle() {
        when(cycles.require(tenantId, cycleId)).thenReturn(openCycle());
        when(templates.require(tenantId, templateId)).thenReturn(activeTemplate());
        when(batches.existsByTenantIdAndCycleIdAndStatusIn(
                        tenantId,
                        cycleId,
                        List.of(
                                AppraisalCycleLaunchBatchStatus.PENDING,
                                AppraisalCycleLaunchBatchStatus.RUNNING)))
                .thenReturn(true);

        LaunchAppraisalCycleRequest req =
                new LaunchAppraisalCycleRequest(
                        tenantId, companyId, templateId, null, null, null, null);

        assertThatThrownBy(() -> service.launch(cycleId, req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(batches, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void launchCreatesAPendingBatchAndPublishesTheKickoffEvent() {
        when(cycles.require(tenantId, cycleId)).thenReturn(openCycle());
        when(templates.require(tenantId, templateId)).thenReturn(activeTemplate());
        when(batches.existsByTenantIdAndCycleIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(batches.save(any(AppraisalCycleLaunchBatch.class)))
                .thenAnswer(
                        invocation -> {
                            AppraisalCycleLaunchBatch b = invocation.getArgument(0);
                            b.setId(UUID.randomUUID());
                            return b;
                        });

        UUID orgUnitA = UUID.randomUUID();
        LaunchAppraisalCycleRequest req =
                new LaunchAppraisalCycleRequest(
                        tenantId,
                        companyId,
                        templateId,
                        List.of(orgUnitA),
                        false,
                        null,
                        EmployeeStatus.ACTIVE);

        AppraisalCycleLaunchBatchResponse response = service.launch(cycleId, req);

        assertThat(response.status()).isEqualTo(AppraisalCycleLaunchBatchStatus.PENDING);
        assertThat(response.cycleId()).isEqualTo(cycleId);

        ArgumentCaptor<AppraisalCycleLaunchBatch> batchCaptor =
                ArgumentCaptor.forClass(AppraisalCycleLaunchBatch.class);
        verify(batches).save(batchCaptor.capture());
        AppraisalCycleLaunchBatch saved = batchCaptor.getValue();
        assertThat(saved.getFilterOrgUnitIds()).isEqualTo(orgUnitA.toString());
        assertThat(saved.isFilterIncludeDescendants()).isFalse();
        assertThat(saved.getFilterEmployeeStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<AppraisalCycleLaunchRequested> eventCaptor =
                ArgumentCaptor.forClass(AppraisalCycleLaunchRequested.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void launchRejectsAClosedCycle() {
        PerformanceCycle closed = openCycle();
        closed.setStatus(PerformanceCycleStatus.CLOSED);
        when(cycles.require(tenantId, cycleId)).thenReturn(closed);
        when(templates.require(tenantId, templateId)).thenReturn(activeTemplate());

        LaunchAppraisalCycleRequest req =
                new LaunchAppraisalCycleRequest(
                        tenantId, companyId, templateId, null, null, null, null);

        assertThatThrownBy(() -> service.launch(cycleId, req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
