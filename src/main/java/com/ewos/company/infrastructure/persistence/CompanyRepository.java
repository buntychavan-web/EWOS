package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository
        extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

    boolean existsByTenantAndCode(Tenant tenant, String code);
}
