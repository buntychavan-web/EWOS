package com.ewos.company.application;

import com.ewos.company.api.dto.BankAccountResponse;
import com.ewos.company.api.dto.CompanyResponse;
import com.ewos.company.api.dto.CompanyVersionResponse;
import com.ewos.company.api.dto.PolicyAssignmentResponse;
import com.ewos.company.api.dto.SharedServiceResponse;
import com.ewos.company.api.dto.StatutoryRegistrationResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyBankAccount;
import com.ewos.company.domain.CompanyPolicyAssignment;
import com.ewos.company.domain.CompanySharedService;
import com.ewos.company.domain.CompanyVersion;
import com.ewos.company.domain.StatutoryRegistration;

/** Pure mappers from entities to API records. Kept isolated so services stay focused. */
final class CompanyMapper {

    private CompanyMapper() {}

    static CompanyVersionResponse toVersion(CompanyVersion v) {
        if (v == null) {
            return null;
        }
        return new CompanyVersionResponse(
                v.getId(),
                v.getEffectiveFrom(),
                v.getEffectiveTo(),
                v.getName(),
                v.getLegalName(),
                v.getLogoUrl(),
                v.getTimezone(),
                v.getCurrency(),
                (int) v.getFiscalYearStartMonth(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                v.getCreatedBy(),
                v.getUpdatedBy(),
                v.getVersion());
    }

    static CompanyResponse toCompany(Company c, CompanyVersion current) {
        return new CompanyResponse(
                c.getId(),
                c.getTenant().getId(),
                c.getCode(),
                c.isActive(),
                toVersion(current),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCreatedBy(),
                c.getUpdatedBy(),
                c.getVersion());
    }

    static StatutoryRegistrationResponse toStatutory(StatutoryRegistration r) {
        return new StatutoryRegistrationResponse(
                r.getId(),
                r.getCompany().getId(),
                r.getKind(),
                r.getRegistrationNumber(),
                r.getJurisdiction(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                r.getVersion());
    }

    static BankAccountResponse toBankAccount(CompanyBankAccount a) {
        return new BankAccountResponse(
                a.getId(),
                a.getCompany().getId(),
                a.getPurpose(),
                a.getAccountName(),
                a.getAccountNumber(),
                a.getBankName(),
                a.getBranch(),
                a.getIfscOrSwift(),
                a.isActive(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.getVersion());
    }

    static PolicyAssignmentResponse toPolicy(CompanyPolicyAssignment p) {
        return new PolicyAssignmentResponse(
                p.getId(),
                p.getCompany().getId(),
                p.getPolicyType(),
                p.getPolicyRef(),
                p.getPolicyLabel(),
                p.getEffectiveFrom(),
                p.getEffectiveTo(),
                p.getVersion());
    }

    static SharedServiceResponse toShared(CompanySharedService s) {
        return new SharedServiceResponse(
                s.getId(),
                s.getCompany().getId(),
                s.getTeamType(),
                s.getTeamRef(),
                s.getTeamLabel(),
                s.getEffectiveFrom(),
                s.getEffectiveTo(),
                s.getVersion());
    }
}
