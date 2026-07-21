package com.ewos.offer.domain;

import java.util.UUID;

/**
 * Framework contract for pre-generating an employee identifier as part of pre-boarding. Deployments
 * plug in company-specific numbering conventions; the default in-tree binding generates {@code
 * EMP-YYYYMM-XXXXXX}.
 */
public interface EmployeeIdGenerator {

    /** Generate the next employee ID candidate for the given tenant + company. */
    String generate(UUID tenantId, UUID companyId);

    /** Identifier of the generator (recorded on the task). */
    String providerId();
}
