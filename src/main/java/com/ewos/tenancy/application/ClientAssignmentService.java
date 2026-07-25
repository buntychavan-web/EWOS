package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.ClientAssignmentResponse;
import com.ewos.tenancy.api.dto.CreateClientAssignmentRequest;
import com.ewos.tenancy.api.dto.UpdateClientAssignmentRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.infrastructure.persistence.ClientAssignmentRepository;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment management is gated purely by the {@code CLIENT_ASSIGNMENT_*} permission codes
 * (granting or revoking client access is itself the admin function) — unlike {@link ClientService}
 * / {@link CompanyService}, this service does not additionally consult {@link ClientAccessGuard}.
 */
@Service
@Transactional
public class ClientAssignmentService {

    private final ClientAssignmentRepository repository;
    private final PayrollServiceProviderRepository providerRepository;
    private final ClientRepository clientRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final TenancyMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ClientAssignmentService(
            ClientAssignmentRepository repository,
            PayrollServiceProviderRepository providerRepository,
            ClientRepository clientRepository,
            ServiceOfferingRepository serviceRepository,
            TenancyMapper mapper) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.clientRepository = clientRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
    }

    public ClientAssignmentResponse create(CreateClientAssignmentRequest request) {
        PayrollServiceProvider provider =
                providerRepository
                        .findById(request.providerId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
        Client client =
                clientRepository
                        .findById(request.clientId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Client not found"));
        ServiceOffering service = null;
        if (request.serviceId() != null) {
            service =
                    serviceRepository
                            .findById(request.serviceId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Service offering not found"));
        }

        ClientAssignment assignment = new ClientAssignment();
        assignment.setProvider(provider);
        assignment.setUserId(request.userId());
        assignment.setClient(client);
        assignment.setService(service);
        assignment.setScopeRole(request.scopeRole());
        assignment.setEffectiveFrom(request.effectiveFrom());
        assignment.setEffectiveTo(request.effectiveTo());
        return mapper.toResponse(repository.save(assignment));
    }

    public ClientAssignmentResponse update(UUID id, UpdateClientAssignmentRequest request) {
        ClientAssignment assignment = require(id);
        if (request.scopeRole() != null) {
            assignment.setScopeRole(request.scopeRole());
        }
        if (request.active() != null) {
            assignment.setActive(request.active());
        }
        if (request.effectiveTo() != null) {
            assignment.setEffectiveTo(request.effectiveTo());
        }
        return mapper.toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public ClientAssignmentResponse getById(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<ClientAssignmentResponse> listByClient(UUID clientId) {
        return repository.findAllByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClientAssignmentResponse> listByProvider(UUID providerId) {
        return repository.findAllByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Revokes (soft-deletes) an assignment. Immediate — the next request from that user re-checks.
     */
    public void revoke(UUID id) {
        repository.delete(require(id));
    }

    private ClientAssignment require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Client assignment not found"));
    }
}
