package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PersonSpecifications {

    private PersonSpecifications() {}

    public static Specification<Person> matching(
            UUID tenantId, String groupPersonId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            }
            if (groupPersonId != null && !groupPersonId.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.upper(root.get("groupPersonId")),
                                groupPersonId.toUpperCase(Locale.ROOT)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
