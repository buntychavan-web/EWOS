package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.AssignPolicyRequest;
import com.ewos.company.api.dto.PolicyAssignmentResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyPolicyAssignment;
import com.ewos.company.domain.PolicyType;
import com.ewos.company.infrastructure.persistence.CompanyPolicyAssignmentRepository;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PolicyAssignmentService {

    private final CompanyPolicyAssignmentRepository repository;
    private final CompanyRepository companyRepository;

    public PolicyAssignmentService(
            CompanyPolicyAssignmentRepository repository, CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    public PolicyAssignmentResponse assign(UUID companyId, AssignPolicyRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Company company = requireCompany(companyId);

        assertNoOverlap(company, req.policyType(), req.effectiveFrom(), req.effectiveTo());

        CompanyPolicyAssignment a = new CompanyPolicyAssignment();
        a.setCompany(company);
        a.setPolicyType(req.policyType());
        a.setPolicyRef(req.policyRef());
        a.setPolicyLabel(req.policyLabel());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        return CompanyMapper.toPolicy(repository.save(a));
    }

    @Transactional(readOnly = true)
    public List<PolicyAssignmentResponse> list(UUID companyId, PolicyType type) {
        Company company = requireCompany(companyId);
        List<CompanyPolicyAssignment> rows =
                type == null
                        ? repository.findByCompany(company)
                        : repository.findByCompanyAndPolicyType(company, type);
        return rows.stream().map(CompanyMapper::toPolicy).toList();
    }

    public PolicyAssignmentResponse retire(UUID companyId, UUID id, LocalDate effectiveTo) {
        CompanyPolicyAssignment a =
                repository
                        .findById(id)
                        .filter(x -> x.getCompany().getId().equals(companyId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Policy assignment not found for company"));
        EffectiveDateValidator.requireOrdered(a.getEffectiveFrom(), effectiveTo);
        a.setEffectiveTo(effectiveTo);
        return CompanyMapper.toPolicy(a);
    }

    private Company requireCompany(UUID id) {
        return companyRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private void assertNoOverlap(Company company, PolicyType type, LocalDate from, LocalDate to) {
        List<CompanyPolicyAssignment> existing =
                repository.findByCompanyAndPolicyType(company, type);
        LocalDate newTo = to == null ? LocalDate.MAX : to;
        for (CompanyPolicyAssignment other : existing) {
            LocalDate otherFrom = other.getEffectiveFrom();
            LocalDate otherTo =
                    other.getEffectiveTo() == null ? LocalDate.MAX : other.getEffectiveTo();
            boolean overlaps = !newTo.isBefore(otherFrom) && !from.isAfter(otherTo);
            if (overlaps) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Policy of type "
                                + type
                                + " already assigned in overlapping window ["
                                + otherFrom
                                + ", "
                                + (other.getEffectiveTo() == null ? "open" : otherTo)
                                + "]");
            }
        }
    }
}
