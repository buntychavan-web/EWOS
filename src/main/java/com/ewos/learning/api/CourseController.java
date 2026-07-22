package com.ewos.learning.api;

import com.ewos.learning.api.dto.CourseResponse;
import com.ewos.learning.api.dto.CreateCourseRequest;
import com.ewos.learning.api.dto.UpdateCourseRequest;
import com.ewos.learning.application.CourseCatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training-courses")
@Tag(name = "Training Courses", description = "Course catalogue (internal, external, online)")
public class CourseController {

    private final CourseCatalogueService catalogue;

    public CourseController(CourseCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Create a training course")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Update a training course")
    public CourseResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest req) {
        return catalogue.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Fetch a training course by id")
    public CourseResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return catalogue.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List active training courses for a company")
    public List<CourseResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return catalogue.listActive(tenantId, companyId);
    }
}
