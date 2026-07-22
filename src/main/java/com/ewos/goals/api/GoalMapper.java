package com.ewos.goals.api;

import com.ewos.employee.domain.Employee;
import com.ewos.goals.api.dto.GoalLibraryItemResponse;
import com.ewos.goals.api.dto.GoalProgressResponse;
import com.ewos.goals.api.dto.GoalReportRowResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalLibraryItem;
import com.ewos.goals.domain.GoalProgressUpdate;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for goal entities. */
@Component
public class GoalMapper {

    public GoalLibraryItemResponse toResponse(GoalLibraryItem g) {
        return new GoalLibraryItemResponse(
                g.getId(),
                g.getTenantId(),
                g.getCompanyId(),
                g.getCode(),
                g.getName(),
                g.getDescription(),
                g.getGoalType(),
                g.getCategory(),
                g.getDefaultWeightage(),
                g.getDefaultTarget(),
                g.getUnitOfMeasure(),
                g.isActive());
    }

    public GoalResponse toResponse(Goal g) {
        return new GoalResponse(
                g.getId(),
                g.getTenantId(),
                g.getCompanyId(),
                g.getLibraryGoal() == null ? null : g.getLibraryGoal().getId(),
                g.getParentGoal() == null ? null : g.getParentGoal().getId(),
                g.getCode(),
                g.getName(),
                g.getDescription(),
                g.getGoalType(),
                g.getScope(),
                g.getEmployee() == null ? null : g.getEmployee().getId(),
                g.getOrgUnitId(),
                g.getPerformanceCycleId(),
                g.getPeriodStart(),
                g.getPeriodEnd(),
                g.getWeightage(),
                g.getTarget(),
                g.getUnitOfMeasure(),
                g.getCurrentValue(),
                g.getProgressPercent(),
                g.getStatus(),
                g.getPriority(),
                g.getReviewScore(),
                g.getReviewNotes(),
                g.getReviewedAt(),
                g.getReviewedBy(),
                g.getClosedAt(),
                g.getClosedBy());
    }

    public GoalProgressResponse toResponse(GoalProgressUpdate u) {
        return new GoalProgressResponse(
                u.getId(),
                u.getGoal() == null ? null : u.getGoal().getId(),
                u.getCurrentValue(),
                u.getProgressPercent(),
                u.getNotes(),
                u.getRecordedAt(),
                u.getRecordedBy());
    }

    public GoalReportRowResponse toReportRow(Goal g) {
        Employee e = g.getEmployee();
        return new GoalReportRowResponse(
                g.getId(),
                g.getCode(),
                g.getName(),
                g.getGoalType(),
                g.getScope(),
                e == null ? null : e.getId(),
                e == null ? null : e.getEmployeeNumber(),
                e == null ? null : displayName(e),
                g.getStatus(),
                g.getWeightage(),
                g.getProgressPercent(),
                g.getReviewScore());
    }

    private String displayName(Employee e) {
        String first = safe(e.getFirstName());
        String last = safe(e.getLastName());
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
