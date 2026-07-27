package com.ewos.workflow.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fans out to whichever registered {@link WorkflowVariableResolver} handles a subject type. */
@Component
public class WorkflowVariableResolverRegistry {

    private final List<WorkflowVariableResolver> resolvers;

    public WorkflowVariableResolverRegistry(List<WorkflowVariableResolver> resolvers) {
        this.resolvers = resolvers;
    }

    /** Empty map (no variables) when no resolver is registered for the subject type. */
    public Map<String, Object> resolve(String subjectType, UUID subjectId) {
        return resolvers.stream()
                .filter(r -> r.supports(subjectType))
                .findFirst()
                .map(r -> r.resolve(subjectId))
                .orElseGet(Map::of);
    }
}
