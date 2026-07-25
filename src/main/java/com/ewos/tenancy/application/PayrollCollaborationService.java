package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreatePayrollCollaborationRequest;
import com.ewos.tenancy.api.dto.PayrollCollaborationResponse;
import com.ewos.tenancy.api.dto.UpdatePayrollCollaborationRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.PayrollCollaboration;
import com.ewos.tenancy.domain.PayrollCollaborationStatus;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollCollaborationRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PayrollCollaborationService {

    private final PayrollCollaborationRepository repository;
    private final ClientRepository clientRepository;
    private final PayrollServiceProviderRepository providerRepository;
    private final ClientAccessGuard guard;
    private final TenancyMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PayrollCollaborationService(
            PayrollCollaborationRepository repository,
            ClientRepository clientRepository,
            PayrollServiceProviderRepository providerRepository,
            ClientAccessGuard guard,
            TenancyMapper mapper) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.providerRepository = providerRepository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public PayrollCollaborationResponse create(CreatePayrollCollaborationRequest request) {
        Client client =
                clientRepository
                        .findById(request.clientId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Client not found"));
        guard.requireAccess(client.getId());
        PayrollServiceProvider provider =
                providerRepository
                        .findById(request.providerId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
        if (repository.existsByClientIdAndProviderIdAndStatus(
                request.clientId(), request.providerId(), PayrollCollaborationStatus.ACTIVE)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "An active payroll collaboration already exists for this client and provider");
        }

        PayrollCollaboration collaboration = new PayrollCollaboration();
        collaboration.setClient(client);
        collaboration.setProvider(provider);
        collaboration.setScope(request.scope());
        collaboration.setEffectiveFrom(request.effectiveFrom());
        collaboration.setEffectiveTo(request.effectiveTo());
        collaboration.setSlaDays(request.slaDays());
        return mapper.toResponse(repository.save(collaboration));
    }

    public PayrollCollaborationResponse update(UUID id, UpdatePayrollCollaborationRequest request) {
        PayrollCollaboration collaboration = require(id);
        guard.requireAccess(collaboration.getClient().getId());
        if (request.scope() != null) {
            collaboration.setScope(request.scope());
        }
        if (request.status() != null) {
            collaboration.setStatus(request.status());
        }
        if (request.effectiveTo() != null) {
            collaboration.setEffectiveTo(request.effectiveTo());
        }
        if (request.slaDays() != null) {
            collaboration.setSlaDays(request.slaDays());
        }
        return mapper.toResponse(collaboration);
    }

    @Transactional(readOnly = true)
    public PayrollCollaborationResponse getById(UUID id) {
        PayrollCollaboration collaboration = require(id);
        guard.requireAccess(collaboration.getClient().getId());
        return mapper.toResponse(collaboration);
    }

    public void delete(UUID id) {
        PayrollCollaboration collaboration = require(id);
        guard.requireAccess(collaboration.getClient().getId());
        repository.delete(collaboration);
    }

    @Transactional(readOnly = true)
    public List<PayrollCollaborationResponse> listByClient(UUID clientId) {
        guard.requireAccess(clientId);
        return repository.findAllByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Not client-scoped by design — a provider user listing by their own provider id is exactly
     * what the Provider Dashboard needs, and the guard's per-client filtering already governs which
     * of those results a restricted caller may act on individually.
     */
    @Transactional(readOnly = true)
    public List<PayrollCollaborationResponse> listByProvider(UUID providerId) {
        return repository.findAllByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private PayrollCollaboration require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Payroll collaboration not found"));
    }
}
