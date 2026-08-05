package com.ewos.exit.api;

import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.SelfResignationRequest;
import com.ewos.exit.application.ExitSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 26 — Employee Self-Service over Exit Management. Requires only authentication (no EXIT_*
 * permission): every method resolves the caller's own employee record and is scoped to it. Mirrors
 * {@code LeaveSelfServiceController} exactly for how "no linked employee" is handled.
 */
@RestController
@RequestMapping("/api/v1/exit/self-service")
@Tag(name = "Exit Self-Service", description = "An employee's own resignation")
public class ExitSelfServiceController {

    private final ExitSelfService exit;

    public ExitSelfServiceController(ExitSelfService exit) {
        this.exit = exit;
    }

    @PostMapping("/resignations")
    @Operation(summary = "Submit the caller's own resignation")
    public ResponseEntity<ResignationResponse> submitMyResignation(
            @Valid @RequestBody SelfResignationRequest request) {
        ResignationResponse created = exit.submitMyResignation(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/exit/self-service/resignations/" + created.id()))
                .body(created);
    }

    @GetMapping("/resignations")
    @Operation(summary = "The caller's own resignation history, most-recent submission last")
    public List<ResignationResponse> myResignations() {
        return exit.myResignations();
    }

    @PostMapping("/resignations/{id}/withdraw")
    @Operation(summary = "Withdraw the caller's own resignation")
    public ResignationResponse withdrawMyResignation(@PathVariable UUID id) {
        return exit.withdrawMyResignation(id);
    }
}
