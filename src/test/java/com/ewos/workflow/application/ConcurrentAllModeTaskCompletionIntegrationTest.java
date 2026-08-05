package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.CompleteTaskRequest;
import com.ewos.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.StateDefinitionSpec;
import com.ewos.workflow.api.dto.TransitionDefinitionSpec;
import com.ewos.workflow.api.dto.WorkflowDefinitionResponse;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.infrastructure.persistence.WorkflowInstanceRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Real-database proof that {@link WorkflowInstanceRepository#lockForUpdate} closes the ALL-mode
 * lost-wakeup race found by a production-readiness audit: two sibling tasks in an {@code ALL}
 * -approval state, completed by two different actors at (as near as a JVM test can force) literally
 * the same instant, must still leave the instance transitioned to its terminal state — not stalled
 * in RUNNING with every task actually resolved and nothing left to act on it. Before the fix, both
 * completions' "is this the last outstanding task" read could each observe the other's
 * not-yet-committed row and conclude "still waiting," advancing neither.
 */
class ConcurrentAllModeTaskCompletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowInstanceService instances;
    @Autowired WorkflowTaskService tasks;
    @Autowired WorkflowInstanceRepository instanceRepository;

    @Test
    void bothSiblingTasksCompletedConcurrentlyStillAdvancesTheInstance() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID approverA = UUID.randomUUID();
        UUID approverB = UUID.randomUUID();

        runAs(approverA);
        WorkflowDefinitionResponse def =
                definitions.create(tenantId, allModeTwoApproverDefinition());

        WorkflowInstanceResponse instance =
                instances.start(
                        new StartInstanceRequest(
                                tenantId,
                                companyId,
                                def.id(),
                                "audit_case",
                                UUID.randomUUID(),
                                null));

        WorkflowTaskResponse taskA =
                tasks.assign(
                        tenantId,
                        instance.id(),
                        new AssignTaskRequest(
                                WorkflowActorType.USER, approverA, null, "APPROVE", null, null));
        WorkflowTaskResponse taskB =
                tasks.assign(
                        tenantId,
                        instance.id(),
                        new AssignTaskRequest(
                                WorkflowActorType.USER, approverB, null, "APPROVE", null, null));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            pool.submit(() -> completeAsActor(tenantId, taskA.id(), approverA, ready, go));
            pool.submit(() -> completeAsActor(tenantId, taskB.id(), approverB, ready, go));
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        WorkflowInstance persisted = instanceRepository.findById(instance.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
    }

    private void completeAsActor(
            UUID tenantId, UUID taskId, UUID actor, CountDownLatch ready, CountDownLatch go) {
        runAs(actor);
        try {
            ready.countDown();
            go.await();
            tasks.complete(tenantId, taskId, new CompleteTaskRequest("APPROVE", null, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void runAs(UUID actorId) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("CLIENT_ADMIN"));
        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, authorities));
        SecurityContextHolder.setContext(context);
    }

    private static CreateWorkflowDefinitionRequest allModeTwoApproverDefinition() {
        return new CreateWorkflowDefinitionRequest(
                "audit-all-mode-" + UUID.randomUUID(),
                "ALL-mode concurrency probe",
                null,
                "audit_case",
                null,
                null,
                null,
                List.of(
                        new StateDefinitionSpec(
                                "DRAFT", "Draft", true, false, 0, null, "ALL", null),
                        new StateDefinitionSpec("DONE", "Done", false, true, 1, null, null, null)),
                List.of(
                        new TransitionDefinitionSpec(
                                "DRAFT", "DONE", "APPROVE", null, false, null)));
    }
}
