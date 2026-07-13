package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyPolicyAssignment;
import com.ewos.company.domain.PolicyType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPolicyAssignmentRepository
        extends JpaRepository<CompanyPolicyAssignment, UUID> {

    List<CompanyPolicyAssignment> findByCompany(Company company);

    List<CompanyPolicyAssignment> findByCompanyAndPolicyType(Company company, PolicyType type);
}
