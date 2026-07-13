package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.person.api.dto.DuplicateRuleResponse;
import com.ewos.person.api.dto.UpdateDuplicateRuleRequest;
import com.ewos.person.domain.PersonDuplicateRule;
import com.ewos.person.infrastructure.persistence.PersonDuplicateRuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** List and update tenant-scoped duplicate-detection rules. Everything is data-driven. */
@Service
@Transactional
public class DuplicateRuleService {

    private final PersonDuplicateRuleRepository repository;
    private final TenantResolver tenantResolver;

    public DuplicateRuleService(
            PersonDuplicateRuleRepository repository, TenantResolver tenantResolver) {
        this.repository = repository;
        this.tenantResolver = tenantResolver;
    }

    @Transactional(readOnly = true)
    public List<DuplicateRuleResponse> list(UUID tenantId) {
        Tenant tenant = tenantResolver.resolve(tenantId);
        return repository.findByTenant(tenant).stream().map(PersonMapper::toRule).toList();
    }

    public DuplicateRuleResponse update(UUID ruleId, UpdateDuplicateRuleRequest req) {
        PersonDuplicateRule r =
                repository
                        .findById(ruleId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Duplicate detection rule not found"));
        r.setEnabled(req.enabled());
        r.setWeight(req.weight());
        return PersonMapper.toRule(r);
    }
}
