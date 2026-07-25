package com.ewos.tenancy.api;

import com.ewos.tenancy.api.dto.ClientAssignmentResponse;
import com.ewos.tenancy.api.dto.ClientResponse;
import com.ewos.tenancy.api.dto.CompanyResponse;
import com.ewos.tenancy.api.dto.PayrollCollaborationResponse;
import com.ewos.tenancy.api.dto.PayrollServiceProviderResponse;
import com.ewos.tenancy.api.dto.ServiceOfferingResponse;
import com.ewos.tenancy.api.dto.TenantResponse;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.domain.PayrollCollaboration;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.domain.Tenant;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping from Tenancy aggregates to their API records. No reflection, no
 * boilerplate-generator: greppable field-for-field mapping so schema drift shows up in a diff —
 * same convention as {@code OrganizationMapper}/{@code PayrollMapper}.
 */
@Component
public final class TenancyMapper {

    public TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                tenant.getCreatedBy(),
                tenant.getUpdatedBy(),
                tenant.getVersionNo());
    }

    public ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getTenant().getId(),
                client.getCode(),
                client.getLegalName(),
                client.getStatus(),
                client.getOnboardedAt(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                client.getCreatedBy(),
                client.getUpdatedBy(),
                client.getVersionNo());
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getTenantId(),
                company.getClient().getId(),
                company.getCode(),
                company.getName(),
                company.getCountryCode(),
                company.getStatus(),
                company.getCreatedAt(),
                company.getUpdatedAt(),
                company.getCreatedBy(),
                company.getUpdatedBy(),
                company.getVersionNo());
    }

    public ServiceOfferingResponse toResponse(ServiceOffering service) {
        return new ServiceOfferingResponse(
                service.getId(),
                service.getTenant().getId(),
                service.getCode(),
                service.getName(),
                service.getDescription(),
                service.getCategory(),
                service.getSortOrder(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt(),
                service.getCreatedBy(),
                service.getUpdatedBy(),
                service.getVersionNo());
    }

    public PayrollServiceProviderResponse toResponse(PayrollServiceProvider provider) {
        return new PayrollServiceProviderResponse(
                provider.getId(),
                provider.getTenant().getId(),
                provider.getCode(),
                provider.getName(),
                provider.getStatus(),
                provider.getCreatedAt(),
                provider.getUpdatedAt(),
                provider.getCreatedBy(),
                provider.getUpdatedBy(),
                provider.getVersionNo());
    }

    public ClientAssignmentResponse toResponse(ClientAssignment assignment) {
        ServiceOffering service = assignment.getService();
        return new ClientAssignmentResponse(
                assignment.getId(),
                assignment.getProvider().getId(),
                assignment.getUserId(),
                assignment.getClient().getId(),
                service != null ? service.getId() : null,
                assignment.getScopeRole(),
                assignment.isActive(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveTo(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                assignment.getCreatedBy(),
                assignment.getUpdatedBy(),
                assignment.getVersionNo());
    }

    public PayrollCollaborationResponse toResponse(PayrollCollaboration collaboration) {
        return new PayrollCollaborationResponse(
                collaboration.getId(),
                collaboration.getClient().getId(),
                collaboration.getProvider().getId(),
                collaboration.getScope(),
                collaboration.getStatus(),
                collaboration.getEffectiveFrom(),
                collaboration.getEffectiveTo(),
                collaboration.getSlaDays(),
                collaboration.getCreatedAt(),
                collaboration.getUpdatedAt(),
                collaboration.getCreatedBy(),
                collaboration.getUpdatedBy(),
                collaboration.getVersionNo());
    }
}
