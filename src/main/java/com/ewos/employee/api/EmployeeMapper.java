package com.ewos.employee.api;

import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EmploymentTypeResponse;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.organization.domain.OrganizationUnit;
import org.springframework.stereotype.Component;

@Component
public final class EmployeeMapper {

    public EmploymentTypeResponse toResponse(EmploymentType type) {
        return new EmploymentTypeResponse(
                type.getId(),
                type.getTenantId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.getSortOrder(),
                type.isActive(),
                type.getCreatedAt(),
                type.getUpdatedAt(),
                type.getCreatedBy(),
                type.getUpdatedBy(),
                type.getVersionNo());
    }

    public EmployeeResponse toResponse(Employee employee) {
        OrganizationUnit unit = employee.getPrimaryOrgUnit();
        EmploymentType type = employee.getEmploymentType();
        Employee manager = employee.getManager();
        return new EmployeeResponse(
                employee.getId(),
                employee.getTenantId(),
                employee.getCompanyId(),
                employee.getPersonId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.getDisplayName(),
                employee.getWorkEmail(),
                employee.getPersonalEmail(),
                employee.getPhone(),
                employee.getDateOfBirth(),
                employee.getGenderCode(),
                unit != null ? unit.getId() : null,
                unit != null ? unit.getCode() : null,
                manager != null ? manager.getId() : null,
                type != null ? type.getId() : null,
                type != null ? type.getCode() : null,
                employee.getHireDate(),
                employee.getTerminationDate(),
                employee.getStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt(),
                employee.getCreatedBy(),
                employee.getUpdatedBy(),
                employee.getVersionNo());
    }
}
