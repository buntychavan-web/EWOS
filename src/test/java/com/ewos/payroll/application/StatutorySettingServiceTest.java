package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateStatutorySettingRequest;
import com.ewos.payroll.domain.StatutorySetting;
import com.ewos.payroll.infrastructure.persistence.StatutorySettingRepository;
import com.ewos.shared.exception.ApiException;
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

/**
 * Effective-dated statutory rate/slab store — the lookup path {@link PayrollCalculator} and
 * statutory extraction rely on for jurisdiction-specific limits (PF ceiling, PT slabs, etc.).
 */
@ExtendWith(MockitoExtension.class)
class StatutorySettingServiceTest {

    @Mock StatutorySettingRepository repository;

    private StatutorySettingService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StatutorySettingService(repository, new PayrollMapper());
        org.mockito.Mockito.lenient()
                .when(repository.save(any(StatutorySetting.class)))
                .thenAnswer(
                        inv -> {
                            StatutorySetting s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(UUID.randomUUID());
                            }
                            return s;
                        });
    }

    private CreateStatutorySettingRequest numericRequest(BigDecimal value) {
        return new CreateStatutorySettingRequest(
                tenantId,
                "IN",
                "PF_WAGE_CEILING",
                "PF wage ceiling",
                null,
                value,
                null,
                LocalDate.of(2026, 4, 1),
                null,
                true);
    }

    @Test
    void createRejectsARequestWithNeitherNumericNorStringValue() {
        CreateStatutorySettingRequest req =
                new CreateStatutorySettingRequest(
                        tenantId,
                        "IN",
                        "PF_WAGE_CEILING",
                        "PF wage ceiling",
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 4, 1),
                        null,
                        true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsABlankStringValueAsEquivalentToMissing() {
        CreateStatutorySettingRequest req =
                new CreateStatutorySettingRequest(
                        tenantId,
                        "IN",
                        "PT_SLAB_LABEL",
                        "PT slab label",
                        null,
                        null,
                        "   ",
                        LocalDate.of(2026, 4, 1),
                        null,
                        true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAcceptsANumericValueWithoutAStringValue() {
        var response = service.create(numericRequest(new BigDecimal("21000")));

        assertThat(response.valueNumeric()).isEqualByComparingTo("21000");
        assertThat(response.jurisdiction()).isEqualTo("IN");
    }

    @Test
    void resolveNumericReturnsEmptyWhenNoActiveRowCoversTheDate() {
        when(repository.findEffective(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 1, 1)))
                .thenReturn(List.of());

        Optional<BigDecimal> resolved =
                service.resolveNumeric(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 1, 1));

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveNumericReturnsTheEffectiveValueForTheAsOfDate() {
        StatutorySetting s = new StatutorySetting();
        s.setValueNumeric(new BigDecimal("21000"));
        when(repository.findEffective(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(s));

        Optional<BigDecimal> resolved =
                service.resolveNumeric(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 5, 1));

        assertThat(resolved).contains(new BigDecimal("21000"));
    }

    @Test
    void resolveNumericUsesTheFirstMatchWhenMultipleEffectiveRowsAreReturned() {
        // The repository query is expected to order by effective date descending; the service
        // trusts that ordering and takes the first result rather than re-sorting.
        StatutorySetting latest = new StatutorySetting();
        latest.setValueNumeric(new BigDecimal("25000"));
        StatutorySetting older = new StatutorySetting();
        older.setValueNumeric(new BigDecimal("21000"));
        when(repository.findEffective(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(latest, older));

        Optional<BigDecimal> resolved =
                service.resolveNumeric(tenantId, "IN", "PF_WAGE_CEILING", LocalDate.of(2026, 6, 1));

        assertThat(resolved).contains(new BigDecimal("25000"));
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownSetting() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownSetting() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesAnExistingSetting() {
        UUID id = UUID.randomUUID();
        StatutorySetting s = new StatutorySetting();
        s.setId(id);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(s));

        service.delete(tenantId, id);

        org.mockito.Mockito.verify(repository).delete(s);
    }

    @Test
    void listByJurisdictionReturnsSettingsOrderedByTheRepositoryQuery() {
        StatutorySetting s = new StatutorySetting();
        s.setJurisdiction("IN");
        when(repository.findAllByTenantIdAndJurisdictionOrderByCodeAscEffectiveFromDesc(
                        tenantId, "IN"))
                .thenReturn(List.of(s));

        var results = service.listByJurisdiction(tenantId, "IN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).jurisdiction()).isEqualTo("IN");
    }
}
