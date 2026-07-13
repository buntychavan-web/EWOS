package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.AssignSharedServiceRequest;
import com.ewos.company.api.dto.SharedServiceResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanySharedService;
import com.ewos.company.domain.TeamType;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import com.ewos.company.infrastructure.persistence.CompanySharedServiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SharedServiceAssignmentService {

    private final CompanySharedServiceRepository repository;
    private final CompanyRepository companyRepository;

    public SharedServiceAssignmentService(
            CompanySharedServiceRepository repository, CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    public SharedServiceResponse assign(UUID companyId, AssignSharedServiceRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Company company = requireCompany(companyId);
        CompanySharedService s = new CompanySharedService();
        s.setCompany(company);
        s.setTeamType(req.teamType());
        s.setTeamRef(req.teamRef());
        s.setTeamLabel(req.teamLabel());
        s.setEffectiveFrom(req.effectiveFrom());
        s.setEffectiveTo(req.effectiveTo());
        return CompanyMapper.toShared(repository.save(s));
    }

    @Transactional(readOnly = true)
    public List<SharedServiceResponse> list(UUID companyId, TeamType type) {
        Company company = requireCompany(companyId);
        List<CompanySharedService> rows =
                type == null
                        ? repository.findByCompany(company)
                        : repository.findByCompanyAndTeamType(company, type);
        return rows.stream().map(CompanyMapper::toShared).toList();
    }

    public SharedServiceResponse retire(UUID companyId, UUID id, LocalDate effectiveTo) {
        CompanySharedService s =
                repository
                        .findById(id)
                        .filter(x -> x.getCompany().getId().equals(companyId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Shared service assignment not found for company"));
        EffectiveDateValidator.requireOrdered(s.getEffectiveFrom(), effectiveTo);
        s.setEffectiveTo(effectiveTo);
        return CompanyMapper.toShared(s);
    }

    private Company requireCompany(UUID id) {
        return companyRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
    }
}
