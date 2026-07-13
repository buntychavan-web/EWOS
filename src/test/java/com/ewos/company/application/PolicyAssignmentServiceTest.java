package com.ewos.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PolicyAssignmentServiceTest {

    @Mock CompanyPolicyAssignmentRepository repository;
    @Mock CompanyRepository companyRepository;

    private PolicyAssignmentService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new PolicyAssignmentService(repository, companyRepository);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setCode("ACME");
        lenient()
                .when(companyRepository.findById(company.getId()))
                .thenReturn(Optional.of(company));
        lenient()
                .when(repository.save(any(CompanyPolicyAssignment.class)))
                .thenAnswer(
                        inv -> {
                            CompanyPolicyAssignment a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
    }

    @Test
    void assignHappyPath() {
        AssignPolicyRequest req =
                new AssignPolicyRequest(
                        PolicyType.LEAVE_POLICY,
                        UUID.randomUUID(),
                        "Standard 2026",
                        LocalDate.of(2026, 1, 1),
                        null);
        when(repository.findByCompanyAndPolicyType(company, PolicyType.LEAVE_POLICY))
                .thenReturn(List.of());

        PolicyAssignmentResponse resp = service.assign(company.getId(), req);
        assertThat(resp.policyType()).isEqualTo(PolicyType.LEAVE_POLICY);
        assertThat(resp.effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void assignRejectsOverlappingWindow() {
        CompanyPolicyAssignment existing = new CompanyPolicyAssignment();
        existing.setCompany(company);
        existing.setPolicyType(PolicyType.LEAVE_POLICY);
        existing.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        existing.setEffectiveTo(null);
        when(repository.findByCompanyAndPolicyType(company, PolicyType.LEAVE_POLICY))
                .thenReturn(List.of(existing));

        AssignPolicyRequest req =
                new AssignPolicyRequest(
                        PolicyType.LEAVE_POLICY,
                        UUID.randomUUID(),
                        "Another",
                        LocalDate.of(2026, 6, 1),
                        null);

        assertThatThrownBy(() -> service.assign(company.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assignRejectsInvertedDates() {
        AssignPolicyRequest req =
                new AssignPolicyRequest(
                        PolicyType.LEAVE_POLICY,
                        UUID.randomUUID(),
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> service.assign(company.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
