package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.ewos.workflow.api.dto.StateDefinitionSpec;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.infrastructure.persistence.WorkflowDefinitionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Covers the tenant-assignment path fixed after a production-readiness audit found {@link
 * WorkflowDefinitionService#create} trusted a client-supplied {@code tenantId} in the request body
 * with no server-side check — {@code WorkflowDefinitionController.create} sent no {@code
 * X-Tenant-Id} header, so {@code TenantHeaderValidationFilter} never ran for this endpoint, and any
 * caller holding {@code WF_WRITE} could plant a workflow definition under an arbitrary tenant. The
 * fix removes {@code tenantId} from the request DTO entirely and sources it only from the header
 * (validated by the filter), matching every sibling method on the same controller.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

    @Mock WorkflowDefinitionRepository repository;
    @Mock ApplicationEventPublisher events;

    private WorkflowDefinitionService service;

    @BeforeEach
    void setUp() {
        service =
                new WorkflowDefinitionService(
                        repository, new WorkflowTransitionPolicy(), new WorkflowMapper(), events);
        lenient()
                .when(repository.save(any(WorkflowDefinition.class)))
                .thenAnswer(
                        inv -> {
                            WorkflowDefinition d = inv.getArgument(0);
                            if (d.getId() == null) {
                                d.setId(UUID.randomUUID());
                            }
                            return d;
                        });
    }

    @Test
    void createAssignsTheCallerSuppliedTenantIdRegardlessOfAnyOtherHint() {
        UUID callerTenantId = UUID.randomUUID();
        CreateWorkflowDefinitionRequest request = minimalRequest();

        var response = service.create(callerTenantId, request);

        assertThat(response.tenantId()).isEqualTo(callerTenantId);
        ArgumentCaptor<WorkflowDefinition> captor =
                ArgumentCaptor.forClass(WorkflowDefinition.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(callerTenantId);
    }

    @Test
    void createScopesTheDuplicateCheckToTheCallerSuppliedTenantId() {
        UUID tenantA = UUID.randomUUID();
        when(repository.existsByTenantIdAndCodeIgnoreCaseAndDefinitionVersion(
                        tenantA, "onboarding", 1))
                .thenReturn(false);

        service.create(tenantA, minimalRequest());

        org.mockito.Mockito.verify(repository)
                .existsByTenantIdAndCodeIgnoreCaseAndDefinitionVersion(tenantA, "onboarding", 1);
    }

    private static CreateWorkflowDefinitionRequest minimalRequest() {
        return new CreateWorkflowDefinitionRequest(
                "onboarding",
                "Onboarding",
                null,
                "onboarding_plan",
                null,
                null,
                null,
                List.of(
                        new StateDefinitionSpec("DRAFT", "Draft", true, false, 0, null, null, null),
                        new StateDefinitionSpec("DONE", "Done", false, true, 1, null, null, null)),
                List.of());
    }
}
