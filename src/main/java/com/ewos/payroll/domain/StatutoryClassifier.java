package com.ewos.payroll.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps pay-component codes to statutory-obligation metadata. Metadata-driven with a small built-in
 * defaults table so a fresh tenant works out of the box; a tenant can override any code by
 * inserting a {@link StatutorySetting} whose code is {@code STAT_MAP.<COMPONENT_CODE>.CODE} etc —
 * that lookup path is reserved for a future release. For now the built-in map is authoritative.
 *
 * <p>Only pay components declared as deductions with a recognised code become statutory deductions.
 * Earnings and non-statutory deductions (e.g. LOAN_REPAY) are ignored.
 */
@Component
public final class StatutoryClassifier {

    /** Immutable built-in classification: componentCode → (jurisdiction, statutoryCode). */
    private static final Map<String, StatutoryClassification> DEFAULTS =
            Map.of(
                    "PF", new StatutoryClassification("IN", "PF"),
                    "PROVIDENT_FUND", new StatutoryClassification("IN", "PF"),
                    "ESI", new StatutoryClassification("IN", "ESI"),
                    "INCOME_TAX", new StatutoryClassification("GLOBAL", "INCOME_TAX"),
                    "TDS", new StatutoryClassification("IN", "TDS"),
                    "PROFESSIONAL_TAX", new StatutoryClassification("IN", "PT"),
                    "SOCIAL_SECURITY", new StatutoryClassification("US", "SOCIAL_SECURITY"),
                    "MEDICARE", new StatutoryClassification("US", "MEDICARE"),
                    "FIT", new StatutoryClassification("US", "FIT"));

    public Optional<StatutoryClassification> classify(String componentCode) {
        if (componentCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFAULTS.get(componentCode.toUpperCase(Locale.ROOT)));
    }

    /** Simple value object; the classifier returns null when a code is not statutory. */
    public record StatutoryClassification(String jurisdiction, String code) {}
}
