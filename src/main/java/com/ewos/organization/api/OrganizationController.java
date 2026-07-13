package com.ewos.organization.api;

import com.ewos.common.exception.ApiError;
import com.ewos.organization.api.dto.CreateOrganizationLevelRequest;
import com.ewos.organization.api.dto.CreateOrganizationNodeRequest;
import com.ewos.organization.api.dto.DeactivateNodeRequest;
import com.ewos.organization.api.dto.InheritanceOverrideResponse;
import com.ewos.organization.api.dto.MergeNodeRequest;
import com.ewos.organization.api.dto.MoveNodeRequest;
import com.ewos.organization.api.dto.NodeVersionResponse;
import com.ewos.organization.api.dto.OrganizationLevelResponse;
import com.ewos.organization.api.dto.OrganizationNodeResponse;
import com.ewos.organization.api.dto.OrganizationNodeTreeResponse;
import com.ewos.organization.api.dto.RenameNodeRequest;
import com.ewos.organization.api.dto.ResolvedInheritanceResponse;
import com.ewos.organization.api.dto.RetireOverrideRequest;
import com.ewos.organization.api.dto.SetInheritanceOverrideRequest;
import com.ewos.organization.api.dto.SplitNodeRequest;
import com.ewos.organization.api.dto.UpdateOrganizationLevelRequest;
import com.ewos.organization.application.OrganizationInheritanceService;
import com.ewos.organization.application.OrganizationLevelService;
import com.ewos.organization.application.OrganizationNodeService;
import com.ewos.organization.domain.InheritableKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
@Tag(
        name = "Organization Structure",
        description =
                "Configurable organization levels, nodes, structural change history, and inheritance overrides")
public class OrganizationController {

    private final OrganizationLevelService levelService;
    private final OrganizationNodeService nodeService;
    private final OrganizationInheritanceService inheritanceService;

    public OrganizationController(
            OrganizationLevelService levelService,
            OrganizationNodeService nodeService,
            OrganizationInheritanceService inheritanceService) {
        this.levelService = levelService;
        this.nodeService = nodeService;
        this.inheritanceService = inheritanceService;
    }

    // -------- Levels --------

    @PostMapping("/levels")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Create a configurable organization level")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Level created",
                content =
                        @Content(
                                schema =
                                        @Schema(implementation = OrganizationLevelResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Level code already exists in tenant",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrganizationLevelResponse> createLevel(
            @Valid @RequestBody CreateOrganizationLevelRequest req) {
        OrganizationLevelResponse created = levelService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/organization/levels/" + created.id()))
                .body(created);
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "List organization levels in display order")
    public List<OrganizationLevelResponse> listLevels(
            @Parameter(description = "Filter by tenant id (defaults to DEFAULT tenant)")
                    @RequestParam(required = false)
                    UUID tenantId) {
        return levelService.list(tenantId);
    }

    @GetMapping("/levels/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Get an organization level")
    public OrganizationLevelResponse getLevel(@PathVariable UUID id) {
        return levelService.getById(id);
    }

    @PutMapping("/levels/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Update an organization level's name or display sequence")
    public OrganizationLevelResponse updateLevel(
            @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationLevelRequest req) {
        return levelService.update(id, req);
    }

    @PatchMapping("/levels/{id}/status")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Activate or deactivate an organization level")
    public OrganizationLevelResponse setLevelActive(
            @PathVariable UUID id, @RequestParam boolean active) {
        return levelService.setActive(id, active);
    }

    @DeleteMapping("/levels/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an organization level (must be unused)")
    public void deleteLevel(@PathVariable UUID id) {
        levelService.softDelete(id);
    }

    // -------- Nodes --------

    @PostMapping("/nodes")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Create an organization node")
    public ResponseEntity<OrganizationNodeResponse> createNode(
            @Valid @RequestBody CreateOrganizationNodeRequest req) {
        OrganizationNodeResponse created = nodeService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/organization/nodes/" + created.id()))
                .body(created);
    }

    @GetMapping("/nodes")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Search organization nodes (paged, filtered)")
    public Page<OrganizationNodeResponse> searchNodes(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID levelId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return nodeService.search(tenantId, levelId, parentId, active, search, pageable);
    }

    @GetMapping("/nodes/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Get an organization node")
    public OrganizationNodeResponse getNode(@PathVariable UUID id) {
        return nodeService.getById(id);
    }

    @GetMapping("/nodes/{id}/tree")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Return the node plus its full subtree")
    public OrganizationNodeTreeResponse tree(@PathVariable UUID id) {
        return nodeService.tree(id);
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Return every root node in a tenant plus its subtree")
    public List<OrganizationNodeTreeResponse> forest(
            @RequestParam(required = false) UUID tenantId) {
        return nodeService.forest(tenantId);
    }

    @PostMapping("/nodes/{id}/rename")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Rename a node; a RENAMED version is recorded")
    public OrganizationNodeResponse rename(
            @PathVariable UUID id, @Valid @RequestBody RenameNodeRequest req) {
        return nodeService.rename(id, req);
    }

    @PostMapping("/nodes/{id}/move")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(
            summary = "Move a node under a new parent",
            description =
                    "Employees referencing the node are automatically re-parented — no employee row is touched.")
    public OrganizationNodeResponse move(
            @PathVariable UUID id, @Valid @RequestBody MoveNodeRequest req) {
        return nodeService.move(id, req);
    }

    @PostMapping("/nodes/{id}/merge")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Merge this node into another; source is deactivated, history preserved")
    public OrganizationNodeResponse merge(
            @PathVariable UUID id, @Valid @RequestBody MergeNodeRequest req) {
        return nodeService.mergeInto(id, req);
    }

    @PostMapping("/nodes/{id}/split")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Split this node into one or more new sibling nodes")
    public List<OrganizationNodeResponse> split(
            @PathVariable UUID id, @Valid @RequestBody SplitNodeRequest req) {
        return nodeService.split(id, req);
    }

    @PostMapping("/nodes/{id}/deactivate")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Deactivate a node — closes its effective window and records a version")
    public OrganizationNodeResponse deactivate(
            @PathVariable UUID id, @Valid @RequestBody DeactivateNodeRequest req) {
        return nodeService.deactivate(id, req);
    }

    @PostMapping("/nodes/{id}/reactivate")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Reactivate a previously deactivated node")
    public OrganizationNodeResponse reactivate(
            @PathVariable UUID id, @RequestParam LocalDate effectiveFrom) {
        return nodeService.reactivate(id, effectiveFrom);
    }

    @DeleteMapping("/nodes/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a leaf node")
    public void deleteNode(@PathVariable UUID id) {
        nodeService.softDelete(id);
    }

    @GetMapping("/nodes/{id}/versions")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "Full structural-change history for a node, newest first")
    public List<NodeVersionResponse> versionHistory(@PathVariable UUID id) {
        return nodeService.versionHistory(id);
    }

    // -------- Inheritance --------

    @PostMapping("/nodes/{id}/inheritance-overrides")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Add an effective-dated inheritance override to a node")
    public ResponseEntity<InheritanceOverrideResponse> setOverride(
            @PathVariable UUID id, @Valid @RequestBody SetInheritanceOverrideRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inheritanceService.setOverride(id, req));
    }

    @GetMapping("/nodes/{id}/inheritance-overrides")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(summary = "List overrides on a node; optionally filter by kind")
    public List<InheritanceOverrideResponse> listOverrides(
            @PathVariable UUID id, @RequestParam(required = false) InheritableKind kind) {
        return inheritanceService.list(id, kind);
    }

    @PatchMapping("/nodes/{id}/inheritance-overrides/{overrideId}/retire")
    @PreAuthorize("hasAuthority('ORGANIZATION_WRITE')")
    @Operation(summary = "Retire an inheritance override by setting its effectiveTo")
    public InheritanceOverrideResponse retireOverride(
            @PathVariable UUID id,
            @PathVariable UUID overrideId,
            @Valid @RequestBody RetireOverrideRequest req) {
        return inheritanceService.retireOverride(id, overrideId, req.effectiveTo());
    }

    @GetMapping("/nodes/{id}/inheritance/{kind}")
    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    @Operation(
            summary = "Resolve an inheritable resource for a node",
            description =
                    "Walks up the parent chain, returning the first live override. `fromOverride=false` "
                            + "indicates nothing in the chain overrides the value and the consumer should fall through "
                            + "to the company-level assignment.")
    public ResolvedInheritanceResponse resolve(
            @PathVariable UUID id,
            @PathVariable InheritableKind kind,
            @RequestParam(required = false) LocalDate asOf) {
        return inheritanceService.resolve(id, kind, asOf);
    }
}
