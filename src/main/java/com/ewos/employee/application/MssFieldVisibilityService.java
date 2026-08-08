package com.ewos.employee.application;

import com.ewos.employee.domain.MssFieldVisibilityConfig;
import com.ewos.employee.infrastructure.persistence.MssFieldVisibilityConfigRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27A — the field-level ACL matrix for Manager Self Service drill-downs (PRD §12, audit
 * finding 3.2). {@link #canManagerView} is the single source of truth every future MSS read must
 * call before including a field in a direct report's drill-down response; there is no other correct
 * way to answer "can this manager see this field."
 *
 * <p>No controller exists yet — configuring visibility is out of Sprint 27A's scope (there is no
 * consumer of these values until 27C's "My Team" drill-down ships); {@link #setVisibility} exists
 * so tests and a future admin endpoint have a single write path, not so it can be called from
 * production code yet.
 */
@Service
public class MssFieldVisibilityService {

    private final MssFieldVisibilityConfigRepository configs;

    public MssFieldVisibilityService(MssFieldVisibilityConfigRepository configs) {
        this.configs = configs;
    }

    /** Default-deny: a field with no configured row for this tenant is masked. */
    @Transactional(readOnly = true)
    public boolean canManagerView(UUID tenantId, String fieldName) {
        return configs.findByTenantIdAndFieldName(tenantId, fieldName)
                .map(MssFieldVisibilityConfig::isManagerCanView)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<MssFieldVisibilityConfig> allForTenant(UUID tenantId) {
        return configs.findAllByTenantId(tenantId);
    }

    @Transactional
    public MssFieldVisibilityConfig setVisibility(
            UUID tenantId, String fieldName, boolean managerCanView) {
        MssFieldVisibilityConfig config =
                configs.findByTenantIdAndFieldName(tenantId, fieldName)
                        .orElseGet(
                                () -> {
                                    MssFieldVisibilityConfig c = new MssFieldVisibilityConfig();
                                    c.setTenantId(tenantId);
                                    c.setFieldName(fieldName);
                                    return c;
                                });
        config.setManagerCanView(managerCanView);
        return configs.save(config);
    }
}
