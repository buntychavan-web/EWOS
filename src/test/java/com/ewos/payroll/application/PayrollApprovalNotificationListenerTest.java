package com.ewos.payroll.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.payroll.domain.events.PayrollApprovalEvent;
import com.ewos.payroll.domain.events.PayrollApprovalEventType;
import com.ewos.workflow.application.ApproverResolver;
import com.ewos.workflow.domain.WorkflowActorType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollApprovalNotificationListenerTest {

    @Mock NotificationService notifications;
    @Mock ApproverResolver approverResolver;

    private PayrollApprovalNotificationListener listener;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new PayrollApprovalNotificationListener(notifications, approverResolver);
    }

    @Test
    void submittedNotifiesEveryResolvedApproverForTheLevelsRole() {
        UUID approverA = UUID.randomUUID();
        UUID approverB = UUID.randomUUID();
        when(approverResolver.resolve(tenantId, companyId, null, "PAYROLL_REVIEWER"))
                .thenReturn(
                        List.of(
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, approverA),
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, approverB)));

        listener.onPayrollApprovalEvent(
                new PayrollApprovalEvent(
                        PayrollApprovalEventType.SUBMITTED,
                        tenantId,
                        companyId,
                        runId,
                        UUID.randomUUID(),
                        1,
                        "PAYROLL_REVIEWER",
                        null,
                        null,
                        null));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(approverA),
                        eq(NotificationType.PAYROLL_APPROVAL_PENDING),
                        any(),
                        any(),
                        any());
        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(approverB),
                        eq(NotificationType.PAYROLL_APPROVAL_PENDING),
                        any(),
                        any(),
                        any());
        verify(notifications, times(2)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void fullyApprovedNotifiesThePreparerOnly() {
        UUID preparer = UUID.randomUUID();

        listener.onPayrollApprovalEvent(
                new PayrollApprovalEvent(
                        PayrollApprovalEventType.FULLY_APPROVED,
                        tenantId,
                        companyId,
                        runId,
                        UUID.randomUUID(),
                        2,
                        null,
                        preparer,
                        UUID.randomUUID(),
                        null));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(preparer),
                        eq(NotificationType.PAYROLL_APPROVAL_FULLY_APPROVED),
                        any(),
                        any(),
                        any());
        verify(approverResolver, never()).resolve(any(), any(), any(), any());
    }

    @Test
    void rejectedNotifiesThePreparerWithTheCommentIncluded() {
        UUID preparer = UUID.randomUUID();

        listener.onPayrollApprovalEvent(
                new PayrollApprovalEvent(
                        PayrollApprovalEventType.REJECTED,
                        tenantId,
                        companyId,
                        runId,
                        UUID.randomUUID(),
                        1,
                        null,
                        preparer,
                        UUID.randomUUID(),
                        "numbers look wrong"));

        org.mockito.ArgumentCaptor<String> bodyCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(preparer),
                        eq(NotificationType.PAYROLL_APPROVAL_REJECTED),
                        any(),
                        bodyCaptor.capture(),
                        any());
        org.assertj.core.api.Assertions.assertThat(bodyCaptor.getValue())
                .contains("numbers look wrong");
    }
}
