package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.StatutoryJurisdictionResponse;
import com.ewos.payroll.application.StatutoryJurisdictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/statutory/jurisdictions")
@Tag(
        name = "Statutory Engine - Jurisdictions",
        description = "Seeded country/state master used to scope PT/LWF")
public class StatutoryJurisdictionController {

    private final StatutoryJurisdictionService service;

    public StatutoryJurisdictionController(StatutoryJurisdictionService service) {
        this.service = service;
    }

    @GetMapping("/{countryCode}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List active jurisdictions (states) for a country")
    public List<StatutoryJurisdictionResponse> forCountry(@PathVariable String countryCode) {
        return service.forCountry(countryCode);
    }
}
