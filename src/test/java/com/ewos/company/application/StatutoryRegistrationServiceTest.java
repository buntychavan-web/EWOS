package com.ewos.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.AddStatutoryRegistrationRequest;
import com.ewos.company.api.dto.StatutoryRegistrationResponse;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.StatutoryRegistration;
import com.ewos.company.domain.StatutoryRegistrationKind;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import com.ewos.company.infrastructure.persistence.StatutoryRegistrationRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StatutoryRegistrationServiceTest {

    @Mock StatutoryRegistrationRepository repository;
    @Mock CompanyRepository companyRepository;

    private StatutoryRegistrationService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new StatutoryRegistrationService(repository, companyRepository);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setCode("ACME");
        lenient()
                .when(companyRepository.findById(company.getId()))
                .thenReturn(Optional.of(company));
        lenient()
                .when(repository.save(any(StatutoryRegistration.class)))
                .thenAnswer(
                        inv -> {
                            StatutoryRegistration r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
    }

    @Test
    void addPanHappyPath() {
        AddStatutoryRegistrationRequest req =
                new AddStatutoryRegistrationRequest(
                        StatutoryRegistrationKind.PAN,
                        "ABCDE1234F",
                        null,
                        LocalDate.of(2026, 1, 1),
                        null);
        when(repository.existsByKindAndRegistrationNumber(
                        StatutoryRegistrationKind.PAN, "ABCDE1234F"))
                .thenReturn(false);

        StatutoryRegistrationResponse resp = service.add(company.getId(), req);
        assertThat(resp.kind()).isEqualTo(StatutoryRegistrationKind.PAN);
        assertThat(resp.registrationNumber()).isEqualTo("ABCDE1234F");
    }

    @Test
    void addPanRejectsDuplicate() {
        AddStatutoryRegistrationRequest req =
                new AddStatutoryRegistrationRequest(
                        StatutoryRegistrationKind.PAN,
                        "ABCDE1234F",
                        null,
                        LocalDate.of(2026, 1, 1),
                        null);
        when(repository.existsByKindAndRegistrationNumber(
                        StatutoryRegistrationKind.PAN, "ABCDE1234F"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.add(company.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addGstDoesNotCheckNationalUniqueness() {
        AddStatutoryRegistrationRequest req =
                new AddStatutoryRegistrationRequest(
                        StatutoryRegistrationKind.GST,
                        "27ABCDE1234F1Z5",
                        "MH",
                        LocalDate.of(2026, 1, 1),
                        null);
        // No existsByKindAndRegistrationNumber stub for GST → nothing checked.
        StatutoryRegistrationResponse resp = service.add(company.getId(), req);
        assertThat(resp.kind()).isEqualTo(StatutoryRegistrationKind.GST);
    }
}
