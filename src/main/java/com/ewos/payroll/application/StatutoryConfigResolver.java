package com.ewos.payroll.application;

import com.ewos.payroll.domain.EsiConfiguration;
import com.ewos.payroll.domain.GratuityConfiguration;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.LwfConfiguration;
import com.ewos.payroll.domain.PfConfiguration;
import com.ewos.payroll.domain.ProfessionalTaxSlab;
import com.ewos.payroll.domain.StatutoryJurisdiction;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.EsiConfigurationRepository;
import com.ewos.payroll.infrastructure.persistence.GratuityConfigurationRepository;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxPolicyRepository;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxSlabRepository;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxSurchargeSlabRepository;
import com.ewos.payroll.infrastructure.persistence.LwfConfigurationRepository;
import com.ewos.payroll.infrastructure.persistence.PfConfigurationRepository;
import com.ewos.payroll.infrastructure.persistence.ProfessionalTaxSlabRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryJurisdictionRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk-resolves one payroll run's whole statutory config snapshot up front — PF/ESI/Gratuity
 * config, Professional Tax slabs and LWF config per jurisdiction, and income-tax slabs/policy for
 * both regimes — so the per-employee calculation loop never issues its own config lookup (the N+1
 * pattern Sprint 19 eliminated for leave/arrears stays eliminated here too).
 */
@Service
public class StatutoryConfigResolver {

    private static final String COUNTRY_CODE = "IN";

    private final PfConfigurationRepository pfConfigurationRepository;
    private final EsiConfigurationRepository esiConfigurationRepository;
    private final GratuityConfigurationRepository gratuityConfigurationRepository;
    private final ProfessionalTaxSlabRepository professionalTaxSlabRepository;
    private final LwfConfigurationRepository lwfConfigurationRepository;
    private final StatutoryJurisdictionRepository statutoryJurisdictionRepository;
    private final IncomeTaxSlabRepository incomeTaxSlabRepository;
    private final IncomeTaxPolicyRepository incomeTaxPolicyRepository;
    private final IncomeTaxSurchargeSlabRepository incomeTaxSurchargeSlabRepository;

    public StatutoryConfigResolver(
            PfConfigurationRepository pfConfigurationRepository,
            EsiConfigurationRepository esiConfigurationRepository,
            GratuityConfigurationRepository gratuityConfigurationRepository,
            ProfessionalTaxSlabRepository professionalTaxSlabRepository,
            LwfConfigurationRepository lwfConfigurationRepository,
            StatutoryJurisdictionRepository statutoryJurisdictionRepository,
            IncomeTaxSlabRepository incomeTaxSlabRepository,
            IncomeTaxPolicyRepository incomeTaxPolicyRepository,
            IncomeTaxSurchargeSlabRepository incomeTaxSurchargeSlabRepository) {
        this.pfConfigurationRepository = pfConfigurationRepository;
        this.esiConfigurationRepository = esiConfigurationRepository;
        this.gratuityConfigurationRepository = gratuityConfigurationRepository;
        this.professionalTaxSlabRepository = professionalTaxSlabRepository;
        this.lwfConfigurationRepository = lwfConfigurationRepository;
        this.statutoryJurisdictionRepository = statutoryJurisdictionRepository;
        this.incomeTaxSlabRepository = incomeTaxSlabRepository;
        this.incomeTaxPolicyRepository = incomeTaxPolicyRepository;
        this.incomeTaxSurchargeSlabRepository = incomeTaxSurchargeSlabRepository;
    }

    public record ConfigSnapshot(
            PfConfiguration pfConfiguration,
            EsiConfiguration esiConfiguration,
            GratuityConfiguration gratuityConfiguration,
            Map<String, StatutoryJurisdiction> jurisdictionByStateCode,
            Map<UUID, List<ProfessionalTaxSlab>> professionalTaxSlabsByJurisdiction,
            Map<UUID, LwfConfiguration> lwfConfigurationByJurisdiction,
            Map<TaxRegime, List<IncomeTaxSlab>> incomeTaxSlabsByRegime,
            Map<TaxRegime, IncomeTaxPolicy> incomeTaxPolicyByRegime,
            Map<TaxRegime, List<IncomeTaxSurchargeSlab>> surchargeSlabsByRegime) {

        public StatutoryJurisdiction jurisdictionFor(String stateCode) {
            return stateCode == null ? null : jurisdictionByStateCode.get(stateCode);
        }

        public List<ProfessionalTaxSlab> professionalTaxSlabsFor(String stateCode) {
            StatutoryJurisdiction jurisdiction = jurisdictionFor(stateCode);
            return jurisdiction == null
                    ? List.of()
                    : professionalTaxSlabsByJurisdiction.getOrDefault(
                            jurisdiction.getId(), List.of());
        }

        public LwfConfiguration lwfConfigurationFor(String stateCode) {
            StatutoryJurisdiction jurisdiction = jurisdictionFor(stateCode);
            return jurisdiction == null
                    ? null
                    : lwfConfigurationByJurisdiction.get(jurisdiction.getId());
        }

        public List<IncomeTaxSlab> incomeTaxSlabsFor(TaxRegime regime) {
            return incomeTaxSlabsByRegime.getOrDefault(regime, List.of());
        }

        public IncomeTaxPolicy incomeTaxPolicyFor(TaxRegime regime) {
            return incomeTaxPolicyByRegime.get(regime);
        }

        public List<IncomeTaxSurchargeSlab> surchargeSlabsFor(TaxRegime regime) {
            return surchargeSlabsByRegime.getOrDefault(regime, List.of());
        }
    }

    @Transactional(readOnly = true)
    public ConfigSnapshot resolve(
            UUID tenantId,
            UUID companyId,
            LocalDate payPeriodDate,
            String fiscalYear,
            Set<String> stateCodes) {
        PfConfiguration pfConfiguration =
                pickEffective(
                        pfConfigurationRepository.findCandidates(tenantId, companyId),
                        payPeriodDate,
                        PfConfiguration::getEffectiveFrom,
                        PfConfiguration::getEffectiveTo);
        EsiConfiguration esiConfiguration =
                pickEffective(
                        esiConfigurationRepository.findCandidates(tenantId, companyId),
                        payPeriodDate,
                        EsiConfiguration::getEffectiveFrom,
                        EsiConfiguration::getEffectiveTo);
        GratuityConfiguration gratuityConfiguration =
                pickEffective(
                        gratuityConfigurationRepository.findCandidates(tenantId, companyId),
                        payPeriodDate,
                        GratuityConfiguration::getEffectiveFrom,
                        GratuityConfiguration::getEffectiveTo);

        Map<String, StatutoryJurisdiction> jurisdictionByStateCode = new HashMap<>();
        for (String stateCode : stateCodes == null ? Set.<String>of() : stateCodes) {
            if (stateCode == null) {
                continue;
            }
            statutoryJurisdictionRepository
                    .findByCountryCodeAndStateCode(COUNTRY_CODE, stateCode)
                    .ifPresent(j -> jurisdictionByStateCode.put(stateCode, j));
        }

        Map<UUID, List<ProfessionalTaxSlab>> ptSlabsByJurisdiction = new HashMap<>();
        Map<UUID, LwfConfiguration> lwfByJurisdiction = new HashMap<>();
        for (StatutoryJurisdiction jurisdiction : jurisdictionByStateCode.values()) {
            List<ProfessionalTaxSlab> ptSlabs =
                    professionalTaxSlabRepository
                            .findActiveForJurisdiction(tenantId, jurisdiction.getId())
                            .stream()
                            .filter(
                                    slab ->
                                            isEffective(
                                                    slab.getEffectiveFrom(),
                                                    slab.getEffectiveTo(),
                                                    payPeriodDate))
                            .toList();
            ptSlabsByJurisdiction.put(jurisdiction.getId(), ptSlabs);

            LwfConfiguration lwf =
                    pickEffective(
                            lwfConfigurationRepository.findActiveForJurisdiction(
                                    tenantId, jurisdiction.getId()),
                            payPeriodDate,
                            LwfConfiguration::getEffectiveFrom,
                            LwfConfiguration::getEffectiveTo);
            if (lwf != null) {
                lwfByJurisdiction.put(jurisdiction.getId(), lwf);
            }
        }

        Map<TaxRegime, List<IncomeTaxSlab>> slabsByRegime = new EnumMap<>(TaxRegime.class);
        Map<TaxRegime, IncomeTaxPolicy> policyByRegime = new EnumMap<>(TaxRegime.class);
        Map<TaxRegime, List<IncomeTaxSurchargeSlab>> surchargeByRegime =
                new EnumMap<>(TaxRegime.class);
        for (TaxRegime regime : TaxRegime.values()) {
            slabsByRegime.put(
                    regime,
                    incomeTaxSlabRepository
                            .findAllByTenantIdAndRegimeAndFiscalYearAndActiveTrueOrderByMinIncomeAsc(
                                    tenantId, regime, fiscalYear));
            incomeTaxPolicyRepository
                    .findByTenantIdAndRegimeAndFiscalYearAndActiveTrue(tenantId, regime, fiscalYear)
                    .ifPresent(p -> policyByRegime.put(regime, p));
            surchargeByRegime.put(
                    regime,
                    incomeTaxSurchargeSlabRepository
                            .findAllByTenantIdAndRegimeAndFiscalYearAndActiveTrueOrderByMinIncomeAsc(
                                    tenantId, regime, fiscalYear));
        }

        return new ConfigSnapshot(
                pfConfiguration,
                esiConfiguration,
                gratuityConfiguration,
                jurisdictionByStateCode,
                ptSlabsByJurisdiction,
                lwfByJurisdiction,
                slabsByRegime,
                policyByRegime,
                surchargeByRegime);
    }

    /**
     * Standalone lookup for callers (e.g. Full &amp; Final settlement) that only need gratuity
     * config.
     */
    @Transactional(readOnly = true)
    public GratuityConfiguration resolveGratuityConfiguration(
            UUID tenantId, UUID companyId, LocalDate asOfDate) {
        return pickEffective(
                gratuityConfigurationRepository.findCandidates(tenantId, companyId),
                asOfDate,
                GratuityConfiguration::getEffectiveFrom,
                GratuityConfiguration::getEffectiveTo);
    }

    private static <T> T pickEffective(
            List<T> candidatesOrderedBySpecificityThenRecency,
            LocalDate date,
            Function<T, LocalDate> effectiveFrom,
            Function<T, LocalDate> effectiveTo) {
        return candidatesOrderedBySpecificityThenRecency.stream()
                .filter(c -> isEffective(effectiveFrom.apply(c), effectiveTo.apply(c), date))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEffective(
            LocalDate effectiveFrom, LocalDate effectiveTo, LocalDate date) {
        return !effectiveFrom.isAfter(date) && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
