package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanySharedService;
import com.ewos.company.domain.TeamType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySharedServiceRepository extends JpaRepository<CompanySharedService, UUID> {

    List<CompanySharedService> findByCompany(Company company);

    List<CompanySharedService> findByCompanyAndTeamType(Company company, TeamType type);
}
