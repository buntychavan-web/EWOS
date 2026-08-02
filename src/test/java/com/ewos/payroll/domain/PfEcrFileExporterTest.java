package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.domain.PfEcrFileExporter.PfEcrRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PfEcrFileExporterTest {

    private final PfEcrFileExporter exporter = new PfEcrFileExporter();

    @Test
    void emitsOneHashTildeDelimitedLinePerRowInEpfoFieldOrder() {
        PfEcrRow row =
                new PfEcrRow(
                        "100200300400",
                        "Asha Rao",
                        new BigDecimal("50000"),
                        new BigDecimal("15000"),
                        new BigDecimal("15000"),
                        new BigDecimal("15000"),
                        new BigDecimal("550"),
                        new BigDecimal("1250"),
                        new BigDecimal("1800"));

        String file = exporter.export(List.of(row));

        assertThat(file)
                .isEqualTo(
                        "100200300400#~#Asha Rao#~#50000#~#15000#~#15000#~#15000#~#550#~#1250#~#1800#~#0#~#0\r\n");
    }

    @Test
    void emitsOneLinePerRowInInputOrder() {
        PfEcrRow first = row("1", "A");
        PfEcrRow second = row("2", "B");

        String file = exporter.export(List.of(first, second));

        String[] lines = file.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("1#~#A");
        assertThat(lines[1]).startsWith("2#~#B");
    }

    @Test
    void blankUanAndNullAmountsDoNotBreakTheLine() {
        PfEcrRow row = new PfEcrRow("", "No Profile", null, null, null, null, null, null, null);

        String file = exporter.export(List.of(row));

        assertThat(file).isEqualTo("#~#No Profile#~#0#~#0#~#0#~#0#~#0#~#0#~#0#~#0#~#0\r\n");
    }

    private static PfEcrRow row(String uan, String name) {
        BigDecimal v = new BigDecimal("1000");
        return new PfEcrRow(uan, name, v, v, v, v, v, v, v);
    }
}
