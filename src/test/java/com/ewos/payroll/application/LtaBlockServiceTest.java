package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.ClaimLtaJourneyRequest;
import com.ewos.payroll.api.dto.CreditLtaAnnualEligibilityRequest;
import com.ewos.payroll.api.dto.LtaBlockClaimResponse;
import com.ewos.payroll.api.dto.LtaBlockSummaryResponse;
import com.ewos.payroll.domain.EmployeeLtaBlockClaim;
import com.ewos.payroll.domain.LtaBlockConfiguration;
import com.ewos.payroll.domain.LtaClaimType;
import com.ewos.payroll.infrastructure.persistence.EmployeeLtaBlockClaimRepository;
import com.ewos.payroll.infrastructure.persistence.LtaBlockConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
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
class LtaBlockServiceTest {

    @Mock LtaBlockConfigurationRepository configurations;
    @Mock EmployeeLtaBlockClaimRepository claims;
    @Mock EmployeeRepository employees;
    @Mock ClientAccessGuard guard;
    private final PayrollMapper mapper = new PayrollMapper();

    private LtaBlockService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LtaBlockService(configurations, claims, employees, mapper, guard);
        org.mockito.Mockito.lenient()
                .when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        org.mockito.Mockito.lenient()
                .when(claims.save(any(EmployeeLtaBlockClaim.class)))
                .thenAnswer(
                        inv -> {
                            EmployeeLtaBlockClaim c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        org.mockito.Mockito.lenient()
                .when(configurations.findActiveCandidates(tenantId, companyId))
                .thenReturn(List.of(config()));
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        return e;
    }

    private LtaBlockConfiguration config() {
        LtaBlockConfiguration c = new LtaBlockConfiguration();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        c.setAnchorBlockStartYear(2022);
        c.setBlockDurationYears(4);
        c.setMaxExemptClaimsPerBlock(2);
        c.setCarryForwardEnabled(true);
        c.setCarryForwardMaxClaims(1);
        c.setEffectiveFrom(LocalDate.of(2022, 1, 1));
        c.setActive(true);
        return c;
    }

    @Test
    void claimJourneyIsTaxFreeWhileTheBlockStillHasAnUnusedClaim() {
        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2022)).thenReturn(List.of());

        LtaBlockClaimResponse response =
                service.claimJourney(
                        new ClaimLtaJourneyRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                "2023-24",
                                LocalDate.of(2023, 6, 1),
                                new BigDecimal("25000"),
                                "Family trip"));

        assertThat(response.claimType()).isEqualTo(LtaClaimType.JOURNEY_CLAIM);
        assertThat(response.taxFreeAmount()).isEqualByComparingTo("25000");
        assertThat(response.taxableAmount()).isEqualByComparingTo("0");
        assertThat(response.blockStartYear()).isEqualTo(2022);
        assertThat(response.blockEndYear()).isEqualTo(2025);
    }

    @Test
    void claimJourneyIsFullyTaxableOnceTheBlockQuotaIsExhausted() {
        EmployeeLtaBlockClaim first = journeyClaim(new BigDecimal("10000"));
        EmployeeLtaBlockClaim second = journeyClaim(new BigDecimal("12000"));
        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2022))
                .thenReturn(List.of(first, second));

        LtaBlockClaimResponse response =
                service.claimJourney(
                        new ClaimLtaJourneyRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                "2024-25",
                                LocalDate.of(2024, 6, 1),
                                new BigDecimal("18000"),
                                "Third journey this block"));

        assertThat(response.taxFreeAmount()).isEqualByComparingTo("0");
        assertThat(response.taxableAmount()).isEqualByComparingTo("18000");
    }

    @Test
    void creditAnnualEligibilityWritesAnAnnualCreditRowAgainstTheCurrentBlock() {
        LtaBlockClaimResponse response =
                service.creditAnnualEligibility(
                        new CreditLtaAnnualEligibilityRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                "2023-24",
                                new BigDecimal("50000"),
                                "Annual entitlement"));

        assertThat(response.claimType()).isEqualTo(LtaClaimType.ANNUAL_CREDIT);
        assertThat(response.ltaCreditedAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void blockSummaryAggregatesCreditedClaimedAndRemainingClaimsFromTheLedger() {
        EmployeeLtaBlockClaim credit = new EmployeeLtaBlockClaim();
        credit.setClaimType(LtaClaimType.ANNUAL_CREDIT);
        credit.setLtaCreditedAmount(new BigDecimal("60000"));
        credit.setAmountClaimed(BigDecimal.ZERO);
        credit.setTaxFreeAmount(BigDecimal.ZERO);
        credit.setTaxableAmount(BigDecimal.ZERO);

        EmployeeLtaBlockClaim claim = journeyClaim(new BigDecimal("20000"));

        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2022))
                .thenReturn(List.of(credit, claim));

        LtaBlockSummaryResponse summary =
                service.blockSummary(tenantId, companyId, employeeId, LocalDate.of(2023, 6, 1));

        assertThat(summary.blockStartYear()).isEqualTo(2022);
        assertThat(summary.blockEndYear()).isEqualTo(2025);
        assertThat(summary.journeyClaimsUsed()).isEqualTo(1);
        assertThat(summary.remainingTaxFreeClaims()).isEqualTo(1);
        assertThat(summary.annualLtaEligibilityCredited()).isEqualByComparingTo("60000");
        assertThat(summary.ltaClaimedAmount()).isEqualByComparingTo("20000");
        assertThat(summary.taxFreeClaimedAmount()).isEqualByComparingTo("20000");
        assertThat(summary.taxableClaimedAmount()).isEqualByComparingTo("0");
        assertThat(summary.openingBalance()).isEqualByComparingTo("0");
        assertThat(summary.closingBalance()).isEqualByComparingTo("40000");
        assertThat(summary.blockHistory()).hasSize(2);
    }

    @Test
    void carryForwardSucceedsInTheFirstCalendarYearOfTheNewBlockWhenAClaimWasUnused() {
        EmployeeLtaBlockClaim usedOnce = journeyClaim(new BigDecimal("15000"));
        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2022))
                .thenReturn(List.of(usedOnce));
        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2026)).thenReturn(List.of());

        LtaBlockClaimResponse response =
                service.carryForwardUnusedClaim(
                        tenantId, companyId, employeeId, LocalDate.of(2026, 2, 1), "Carried over");

        assertThat(response.claimType()).isEqualTo(LtaClaimType.BLOCK_CARRY_FORWARD);
        assertThat(response.carriedForwardFromPreviousBlock()).isTrue();
        assertThat(response.blockStartYear()).isEqualTo(2026);
    }

    @Test
    void carryForwardRejectedOutsideTheFirstCalendarYearOfTheNewBlock() {
        assertThatThrownBy(
                        () ->
                                service.carryForwardUnusedClaim(
                                        tenantId,
                                        companyId,
                                        employeeId,
                                        LocalDate.of(2027, 2, 1),
                                        null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void carryForwardRejectedWhenNoClaimWasLeftUnusedInThePreviousBlock() {
        EmployeeLtaBlockClaim first = journeyClaim(new BigDecimal("10000"));
        EmployeeLtaBlockClaim second = journeyClaim(new BigDecimal("12000"));
        when(claims.findAllForEmployeeAndBlock(tenantId, employeeId, 2022))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(
                        () ->
                                service.carryForwardUnusedClaim(
                                        tenantId,
                                        companyId,
                                        employeeId,
                                        LocalDate.of(2026, 2, 1),
                                        null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void requireConfigurationRejectsWhenNoActiveConfigurationExistsForTheCompany() {
        when(configurations.findActiveCandidates(tenantId, companyId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.blockSummary(
                                        tenantId, companyId, employeeId, LocalDate.of(2023, 1, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private static EmployeeLtaBlockClaim journeyClaim(BigDecimal amount) {
        EmployeeLtaBlockClaim c = new EmployeeLtaBlockClaim();
        c.setClaimType(LtaClaimType.JOURNEY_CLAIM);
        c.setAmountClaimed(amount);
        c.setTaxFreeAmount(amount);
        c.setTaxableAmount(BigDecimal.ZERO);
        c.setLtaCreditedAmount(BigDecimal.ZERO);
        return c;
    }
}
