package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.AddStatutoryRegistrationRequest;
import com.ewos.company.api.dto.StatutoryRegistrationResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.StatutoryRegistration;
import com.ewos.company.domain.StatutoryRegistrationKind;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import com.ewos.company.infrastructure.persistence.StatutoryRegistrationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StatutoryRegistrationService {

    private final StatutoryRegistrationRepository repository;
    private final CompanyRepository companyRepository;

    public StatutoryRegistrationService(
            StatutoryRegistrationRepository repository, CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    public StatutoryRegistrationResponse add(UUID companyId, AddStatutoryRegistrationRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Company company =
                companyRepository
                        .findById(companyId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));

        if (req.kind() == StatutoryRegistrationKind.PAN
                && repository.existsByKindAndRegistrationNumber(
                        StatutoryRegistrationKind.PAN, req.registrationNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAN '" + req.registrationNumber() + "' is already registered");
        }

        StatutoryRegistration entry = new StatutoryRegistration();
        entry.setCompany(company);
        entry.setKind(req.kind());
        entry.setRegistrationNumber(req.registrationNumber());
        entry.setJurisdiction(req.jurisdiction());
        entry.setEffectiveFrom(req.effectiveFrom());
        entry.setEffectiveTo(req.effectiveTo());
        return CompanyMapper.toStatutory(repository.save(entry));
    }

    @Transactional(readOnly = true)
    public List<StatutoryRegistrationResponse> list(
            UUID companyId, StatutoryRegistrationKind kind) {
        Company company =
                companyRepository
                        .findById(companyId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
        List<StatutoryRegistration> rows =
                kind == null
                        ? repository.findByCompany(company)
                        : repository.findByCompanyAndKind(company, kind);
        return rows.stream().map(CompanyMapper::toStatutory).toList();
    }

    public StatutoryRegistrationResponse retire(UUID companyId, UUID id, LocalDate effectiveTo) {
        StatutoryRegistration entry =
                repository
                        .findById(id)
                        .filter(e -> e.getCompany().getId().equals(companyId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Statutory registration not found for company"));
        EffectiveDateValidator.requireOrdered(entry.getEffectiveFrom(), effectiveTo);
        entry.setEffectiveTo(effectiveTo);
        return CompanyMapper.toStatutory(entry);
    }
}
