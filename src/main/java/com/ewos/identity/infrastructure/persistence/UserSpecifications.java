package com.ewos.identity.infrastructure.persistence;

import com.ewos.identity.api.dto.UserSearchCriteria;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> matching(UserSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (c.username() != null && !c.username().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("username")),
                                "%" + c.username().toLowerCase(Locale.ROOT) + "%"));
            }
            if (c.email() != null && !c.email().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("email")),
                                "%" + c.email().toLowerCase(Locale.ROOT) + "%"));
            }
            if (c.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), c.enabled()));
            }
            if (c.roleId() != null) {
                Join<User, Role> roles = root.join("roles", JoinType.LEFT);
                predicates.add(cb.equal(roles.get("id"), c.roleId()));
                query.distinct(true);
            }
            if (c.createdAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdAfter()));
            }
            if (c.createdBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), c.createdBefore()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
