package com.ewos.workflow.application;

import java.util.Map;
import java.util.UUID;

/**
 * Extension point a downstream module implements to expose its own business data to workflow
 * guard-expression evaluation, without the workflow engine needing to know about leave requests,
 * expenses, or any other subject type (the engine stays metadata-driven — see {@code
 * docs/modules/workflow.md}). Register a Spring bean per subject type; {@link
 * WorkflowVariableResolverRegistry} fans out to whichever one matches the running instance.
 */
public interface WorkflowVariableResolver {

    boolean supports(String subjectType);

    /** Read-only snapshot of business fields to expose to a guard expression as SpEL variables. */
    Map<String, Object> resolve(UUID subjectId);
}
