package com.ewos.attendance.application;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.CreateTimeEntryRequest;
import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.TimeEntrySource;
import com.ewos.attendance.domain.events.AttendanceEvent;
import com.ewos.attendance.domain.events.AttendanceEventType;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimeEntryService {

    private final TimeEntryRepository entries;
    private final EmployeeRepository employees;
    private final AttendanceMapper mapper;
    private final ApplicationEventPublisher events;

    public TimeEntryService(
            TimeEntryRepository entries,
            EmployeeRepository employees,
            AttendanceMapper mapper,
            ApplicationEventPublisher events) {
        this.entries = entries;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    public TimeEntryResponse record(CreateTimeEntryRequest request) {
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Employee belongs to a different company than the time entry claims");
        }

        TimeEntry entry = new TimeEntry();
        entry.setTenantId(request.tenantId());
        entry.setCompanyId(request.companyId());
        entry.setEmployee(employee);
        entry.setEventType(request.eventType());
        entry.setOccurredAt(request.occurredAt());
        entry.setSource(request.source() != null ? request.source() : TimeEntrySource.MANUAL);
        entry.setLocation(request.location());
        entry.setNotes(request.notes());

        AttendanceEventType eventType = AttendanceEventType.TIME_ENTRY_RECORDED;
        if (request.correctionOf() != null) {
            TimeEntry original =
                    entries.findByIdAndTenantId(request.correctionOf(), request.tenantId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Original entry to correct not found"));
            entry.setCorrectionOf(original);
            entry.setSource(TimeEntrySource.CORRECTION);
            eventType = AttendanceEventType.TIME_ENTRY_CORRECTED;
        }

        TimeEntry saved = entries.save(entry);
        publish(eventType, saved);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TimeEntryResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> forEmployeeInRange(
            UUID tenantId, UUID employeeId, Instant from, Instant to) {
        return entries.findForEmployeeInRange(tenantId, employeeId, from, to).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> recentForEmployee(UUID tenantId, UUID employeeId) {
        return entries.findRecentForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private TimeEntry require(UUID tenantId, UUID id) {
        return entries.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Time entry not found"));
    }

    private void publish(AttendanceEventType type, TimeEntry entry) {
        events.publishEvent(
                new AttendanceEvent(
                        type,
                        entry.getTenantId(),
                        entry.getCompanyId(),
                        entry.getEmployee() != null ? entry.getEmployee().getId() : null,
                        entry.getId(),
                        null,
                        null,
                        null,
                        null,
                        currentActor(),
                        Instant.now()));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
