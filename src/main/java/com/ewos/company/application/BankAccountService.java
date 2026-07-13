package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.AddBankAccountRequest;
import com.ewos.company.api.dto.BankAccountResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyBankAccount;
import com.ewos.company.infrastructure.persistence.CompanyBankAccountRepository;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankAccountService {

    private final CompanyBankAccountRepository repository;
    private final CompanyRepository companyRepository;

    public BankAccountService(
            CompanyBankAccountRepository repository, CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    public BankAccountResponse add(UUID companyId, AddBankAccountRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Company company =
                companyRepository
                        .findById(companyId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));

        CompanyBankAccount account = new CompanyBankAccount();
        account.setCompany(company);
        account.setPurpose(req.purpose());
        account.setAccountName(req.accountName());
        account.setAccountNumber(req.accountNumber());
        account.setBankName(req.bankName());
        account.setBranch(req.branch());
        account.setIfscOrSwift(req.ifscOrSwift());
        account.setActive(true);
        account.setEffectiveFrom(req.effectiveFrom());
        account.setEffectiveTo(req.effectiveTo());
        return CompanyMapper.toBankAccount(repository.save(account));
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID companyId) {
        Company company =
                companyRepository
                        .findById(companyId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
        return repository.findByCompany(company).stream()
                .map(CompanyMapper::toBankAccount)
                .toList();
    }

    public BankAccountResponse setStatus(UUID companyId, UUID accountId, boolean active) {
        CompanyBankAccount account =
                repository
                        .findById(accountId)
                        .filter(a -> a.getCompany().getId().equals(companyId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Bank account not found for company"));
        account.setActive(active);
        return CompanyMapper.toBankAccount(account);
    }
}
