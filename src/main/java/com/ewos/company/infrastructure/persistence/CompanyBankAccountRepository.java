package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyBankAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBankAccountRepository extends JpaRepository<CompanyBankAccount, UUID> {

    List<CompanyBankAccount> findByCompany(Company company);
}
