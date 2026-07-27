package com.ewos.integration.domain;

/** Outcome of a single {@link IntegrationAdapter} execution. */
public record IntegrationAdapterResult(
        IntegrationExecutionOutcome outcome,
        ErrorClassification errorClassification,
        String detail) {

    public static IntegrationAdapterResult success(String detail) {
        return new IntegrationAdapterResult(IntegrationExecutionOutcome.SUCCESS, null, detail);
    }

    public static IntegrationAdapterResult failure(
            ErrorClassification classification, String detail) {
        return new IntegrationAdapterResult(
                IntegrationExecutionOutcome.FAILURE, classification, detail);
    }
}
