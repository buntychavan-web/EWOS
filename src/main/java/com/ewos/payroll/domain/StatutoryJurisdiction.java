package com.ewos.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Lightweight state/country master used to scope Professional Tax and Labour Welfare Fund
 * configuration. India ships seeded with all 28 states + 8 union territories (migration V52); other
 * countries can be added the same way without a schema change.
 */
@Entity
@Table(name = "statutory_jurisdictions")
public class StatutoryJurisdiction {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "state_code", nullable = false, length = 8)
    private String stateCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
