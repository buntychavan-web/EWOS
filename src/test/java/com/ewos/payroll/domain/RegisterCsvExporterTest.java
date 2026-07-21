package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.api.dto.reports.RegisterResponse;
import com.ewos.payroll.api.dto.reports.RegisterRowResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterCsvExporterTest {

    private final RegisterCsvExporter exporter = new RegisterCsvExporter();

    @Test
    void emitsHeaderAndRows() {
        RegisterRowResponse row =
                new RegisterRowResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "EMP-1",
                        "Ada Lovelace",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        LocalDate.of(2026, 8, 5),
                        "USD",
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"),
                        "REGULAR",
                        "FINALIZED");
        RegisterResponse r =
                new RegisterResponse(
                        "SALARY_REGISTER",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"),
                        List.of(row));

        String csv = exporter.export(r);
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("payslip_id,payroll_run_id,");
        assertThat(lines[1]).contains("Ada Lovelace").contains("REGULAR").contains("FINALIZED");
    }

    @Test
    void handlesNullValues() {
        RegisterRowResponse row =
                new RegisterRowResponse(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);
        RegisterResponse r =
                new RegisterResponse(
                        "SALARY_REGISTER",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of(row));
        String csv = exporter.export(r);
        assertThat(csv).contains(",,");
    }
}
