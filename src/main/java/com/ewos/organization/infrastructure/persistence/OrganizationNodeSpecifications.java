package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.domain.OrganizationNode;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class OrganizationNodeSpecifications {

    private OrganizationNodeSpecifications() {}

    public static Specification<OrganizationNode> matching(
            UUID tenantId, UUID levelId, UUID parentId, Boolean active, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            }
            if (levelId != null) {
                predicates.add(cb.equal(root.get("level").get("id"), levelId));
            }
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parent").get("id"), parentId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String needle = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("code")), needle),
                                cb.like(cb.lower(root.get("name")), needle)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
