package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayslipBrandingConfigurationRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayslipPdfServiceTest {

    @Mock PayslipService payslips;
    @Mock PayslipBrandingConfigurationRepository branding;
    @Mock PayslipSignatureService signatureService;

    private PayslipPdfService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID payslipId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PayslipPdfService(
                        payslips, branding, new PayslipPdfGenerationService(), signatureService);
        org.mockito.Mockito.lenient()
                .when(branding.findByTenantIdAndCompanyIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(signatureService.sign(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static Payslip payslip(String employeeNumber, LocalDate periodStart) {
        Payslip p = new Payslip();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setEmployeeNameSnapshot("Test Employee");
        p.setEmployeeNumberSnapshot(employeeNumber);
        p.setPeriodStart(periodStart);
        p.setPeriodEnd(periodStart.plusDays(29));
        p.setPayDate(periodStart.plusMonths(1));
        p.setCurrency("INR");
        p.setGrossAmount(new BigDecimal("50000"));
        p.setDeductionsAmount(BigDecimal.ZERO);
        p.setNetAmount(new BigDecimal("50000"));
        return p;
    }

    @Test
    void generateForAdminDelegatesAccessCheckAndSignsTheResult() {
        Payslip slip = payslip("E100", LocalDate.of(2026, 8, 1));
        when(payslips.requireForAdmin(tenantId, payslipId)).thenReturn(slip);

        byte[] pdf = service.generateForAdmin(tenantId, payslipId);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
        verify(payslips).requireForAdmin(tenantId, payslipId);
        verify(signatureService).sign(any(), any(), any());
    }

    @Test
    void generateForSelfUsesOwnershipCheckRatherThanAdminAccess() {
        Payslip slip = payslip("E100", LocalDate.of(2026, 8, 1));
        when(payslips.requireOwn(tenantId, employeeId, payslipId)).thenReturn(slip);

        byte[] pdf = service.generateForSelf(tenantId, employeeId, payslipId);

        assertThat(pdf).isNotEmpty();
        verify(payslips).requireOwn(tenantId, employeeId, payslipId);
    }

    @Test
    void generateZipForRunProducesOnePdfEntryPerPayslip() throws Exception {
        Payslip first = payslip("E100", LocalDate.of(2026, 8, 1));
        Payslip second = payslip("E101", LocalDate.of(2026, 8, 1));
        when(payslips.entitiesForRun(tenantId, runId)).thenReturn(List.of(first, second));

        byte[] zip = service.generateZipForRun(tenantId, runId);

        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
        }
        assertThat(entryCount).isEqualTo(2);
    }
}
