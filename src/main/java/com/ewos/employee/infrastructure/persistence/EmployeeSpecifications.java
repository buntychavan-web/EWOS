package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.api.dto.EmployeeSearchCriteria;
import com.ewos.employee.domain.Employee;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class EmployeeSpecifications {

    private EmployeeSpecifications() {}

    public static Specification<Employee> matching(EmployeeSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.tenantId() == null) {
                throw new IllegalArgumentException("tenantId is required on employee search");
            }
            predicates.add(cb.equal(root.get("tenantId"), c.tenantId()));
            if (c.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), c.companyId()));
            }
            if (c.primaryOrgUnitId() != null) {
                predicates.add(
                        cb.equal(root.get("primaryOrgUnit").get("id"), c.primaryOrgUnitId()));
            }
            if (c.managerEmployeeId() != null) {
                predicates.add(cb.equal(root.get("manager").get("id"), c.managerEmployeeId()));
            }
            if (c.employmentTypeId() != null) {
                predicates.add(
                        cb.equal(root.get("employmentType").get("id"), c.employmentTypeId()));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.employeeNumber() != null && !c.employeeNumber().isBlank()) {
                predicates.add(
                        cb.like(cb.lower(root.get("employeeNumber")), like(c.employeeNumber())));
            }
            if (c.namePart() != null && !c.namePart().isBlank()) {
                String pattern = like(c.namePart());
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("firstName")), pattern),
                                cb.like(cb.lower(root.get("lastName")), pattern),
                                cb.like(cb.lower(root.get("displayName")), pattern)));
            }
            if (c.workEmail() != null && !c.workEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("workEmail")), like(c.workEmail())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String like(String raw) {
        return "%" + raw.toLowerCase(Locale.ROOT) + "%";
    }
}
