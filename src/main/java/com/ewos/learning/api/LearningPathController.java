package com.ewos.learning.api;

import com.ewos.learning.api.dto.AddPathCourseRequest;
import com.ewos.learning.api.dto.CreateLearningPathRequest;
import com.ewos.learning.api.dto.LearningPathResponse;
import com.ewos.learning.api.dto.PathCourseResponse;
import com.ewos.learning.application.LearningPathService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-paths")
@Tag(name = "Learning Paths", description = "Sequences of courses")
public class LearningPathController {

    private final LearningPathService paths;

    public LearningPathController(LearningPathService paths) {
        this.paths = paths;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Create a learning path")
    public ResponseEntity<LearningPathResponse> create(
            @Valid @RequestBody CreateLearningPathRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paths.create(req));
    }

    @PostMapping("/{id}/courses")
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Add a course to a learning path")
    public ResponseEntity<PathCourseResponse> addCourse(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddPathCourseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paths.addCourse(tenantId, id, req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Fetch a learning path by id")
    public LearningPathResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return paths.getById(tenantId, id);
    }

    @GetMapping("/{id}/courses")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List courses on a learning path")
    public List<PathCourseResponse> listCourses(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return paths.listCourses(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List active learning paths for a company")
    public List<LearningPathResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return paths.listActive(tenantId, companyId);
    }
}
