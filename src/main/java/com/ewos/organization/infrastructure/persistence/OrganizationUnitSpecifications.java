package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.api.dto.OrganizationUnitSearchCriteria;
import com.ewos.organization.domain.OrganizationUnit;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic search-predicate builder for {@link OrganizationUnit}. Tenant scoping is mandatory; the
 * caller must always pass a non-null {@code tenantId} in the criteria — the specification refuses
 * to build without it.
 */
public final class OrganizationUnitSpecifications {

    private OrganizationUnitSpecifications() {}

    public static Specification<OrganizationUnit> matching(OrganizationUnitSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.tenantId() == null) {
                throw new IllegalArgumentException("tenantId is required on organization search");
            }
            predicates.add(cb.equal(root.get("tenantId"), c.tenantId()));

            if (c.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), c.companyId()));
            }
            if (c.unitTypeId() != null) {
                predicates.add(cb.equal(root.get("unitType").get("id"), c.unitTypeId()));
            }
            if (c.parentId() != null) {
                predicates.add(cb.equal(root.get("parent").get("id"), c.parentId()));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.code() != null && !c.code().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("code")), like(c.code())));
            }
            if (c.name() != null && !c.name().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), like(c.name())));
            }
            if (c.countryCode() != null && !c.countryCode().isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.upper(root.get("countryCode")),
                                c.countryCode().toUpperCase(Locale.ROOT)));
            }
            if (c.managerPersonId() != null) {
                predicates.add(cb.equal(root.get("managerPersonId"), c.managerPersonId()));
            }
            if (Boolean.TRUE.equals(c.rootsOnly())) {
                predicates.add(cb.isNull(root.get("parent")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String like(String raw) {
        return "%" + raw.toLowerCase(Locale.ROOT) + "%";
    }
}
