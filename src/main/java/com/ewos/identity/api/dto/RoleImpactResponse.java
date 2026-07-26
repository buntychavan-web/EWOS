package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
        description =
                "Sprint 1.4 Role Usage Impact Analysis — everything an admin needs to check before"
                        + " editing or deleting a role. canDelete mirrors exactly what DELETE"
                        + " /roles/{id} itself enforces, so the two can never disagree.")
public record RoleImpactResponse(
        UUID roleId,
        String roleName,
        boolean systemRole,
        long assignedUserCount,
        List<CompanyUsage> companies,
        List<DepartmentUsage> departments,
        int pendingWorkflowTaskCount,
        boolean canDelete) {

    public record CompanyUsage(UUID companyId, long userCount) {}

    public record DepartmentUsage(UUID orgUnitId, String orgUnitCode, long userCount) {}
}
