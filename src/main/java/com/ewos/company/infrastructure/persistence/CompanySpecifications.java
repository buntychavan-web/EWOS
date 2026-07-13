package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class CompanySpecifications {

    private CompanySpecifications() {}

    public static Specification<Company> matching(
            UUID tenantId, String code, Boolean active, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("code")), code.toLowerCase(Locale.ROOT)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                // Match against code (name lives in CompanyVersion; can be extended later).
                String needle = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("code")), needle));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
