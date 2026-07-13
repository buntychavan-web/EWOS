package com.ewos.person.api;

import com.ewos.common.exception.ApiError;
import com.ewos.person.api.dto.AddAddressRequest;
import com.ewos.person.api.dto.AddEducationRequest;
import com.ewos.person.api.dto.AddEmergencyContactRequest;
import com.ewos.person.api.dto.AddFamilyMemberRequest;
import com.ewos.person.api.dto.AddIdentityDocumentRequest;
import com.ewos.person.api.dto.AddressResponse;
import com.ewos.person.api.dto.ContactResponse;
import com.ewos.person.api.dto.CreatePersonRequest;
import com.ewos.person.api.dto.DuplicateCheckRequest;
import com.ewos.person.api.dto.DuplicateCheckResponse;
import com.ewos.person.api.dto.DuplicateRuleResponse;
import com.ewos.person.api.dto.EducationResponse;
import com.ewos.person.api.dto.EmergencyContactResponse;
import com.ewos.person.api.dto.FamilyMemberResponse;
import com.ewos.person.api.dto.IdentityDocumentResponse;
import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.api.dto.PersonStatusRequest;
import com.ewos.person.api.dto.PersonVersionResponse;
import com.ewos.person.api.dto.ReadinessResponse;
import com.ewos.person.api.dto.RetireRequest;
import com.ewos.person.api.dto.SetContactRequest;
import com.ewos.person.api.dto.UpdateDuplicateRuleRequest;
import com.ewos.person.api.dto.UpdatePersonProfileRequest;
import com.ewos.person.application.DuplicateDetectionService;
import com.ewos.person.application.DuplicateRuleService;
import com.ewos.person.application.PersonAddressService;
import com.ewos.person.application.PersonContactService;
import com.ewos.person.application.PersonEducationService;
import com.ewos.person.application.PersonEmergencyContactService;
import com.ewos.person.application.PersonFamilyService;
import com.ewos.person.application.PersonIdentityDocumentService;
import com.ewos.person.application.PersonSearchService;
import com.ewos.person.application.PersonService;
import com.ewos.person.application.ProfileReadinessService;
import com.ewos.person.domain.AddressKind;
import com.ewos.person.domain.IdentityDocumentKind;
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
@RequestMapping("/api/v1/persons")
@Tag(
        name = "Person Engine",
        description =
                "Permanent identity — profile versions, contacts, addresses, emergency contacts,"
                        + " family, education, identity documents, duplicate detection, readiness")
public class PersonController {

    private final PersonService personService;
    private final PersonContactService contactService;
    private final PersonAddressService addressService;
    private final PersonEmergencyContactService emergencyService;
    private final PersonFamilyService familyService;
    private final PersonEducationService educationService;
    private final PersonIdentityDocumentService documentService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final DuplicateRuleService duplicateRuleService;
    private final ProfileReadinessService readinessService;
    private final PersonSearchService searchService;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PersonController(
            PersonService personService,
            PersonContactService contactService,
            PersonAddressService addressService,
            PersonEmergencyContactService emergencyService,
            PersonFamilyService familyService,
            PersonEducationService educationService,
            PersonIdentityDocumentService documentService,
            DuplicateDetectionService duplicateDetectionService,
            DuplicateRuleService duplicateRuleService,
            ProfileReadinessService readinessService,
            PersonSearchService searchService) {
        this.personService = personService;
        this.contactService = contactService;
        this.addressService = addressService;
        this.emergencyService = emergencyService;
        this.familyService = familyService;
        this.educationService = educationService;
        this.documentService = documentService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.duplicateRuleService = duplicateRuleService;
        this.readinessService = readinessService;
        this.searchService = searchService;
    }

    // -------- Persons --------

    @PostMapping
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Create a Person; runs duplicate detection first")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Person created",
                content = @Content(schema = @Schema(implementation = PersonResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Duplicates detected — resubmit with overrideDuplicates=true",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody CreatePersonRequest req) {
        PersonResponse created = personService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/persons/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "Paged list of persons")
    public Page<PersonResponse> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String groupPersonId,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return personService.search(tenantId, groupPersonId, active, pageable);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(
            summary = "Fast search by Person ID / name / mobile / email / PAN / passport / Aadhaar")
    public List<PersonResponse> search(@RequestParam("q") String query) {
        return searchService.search(query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "Get a Person with its current or as-of profile version")
    public PersonResponse getById(
            @PathVariable UUID id,
            @Parameter(description = "Return the version effective at this date (ISO-8601)")
                    @RequestParam(required = false)
                    LocalDate asOf) {
        return personService.getById(id, asOf);
    }

    @GetMapping("/by-group-id/{groupPersonId}")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "Look up a Person by their group Person ID (e.g. P000000001)")
    public PersonResponse getByGroupPersonId(@PathVariable String groupPersonId) {
        return personService.getByGroupPersonId(groupPersonId);
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(
            summary = "Update the Person profile — creates a new effective-dated version",
            description =
                    "Closes the current version the day before the new profile's effectiveFrom "
                            + "and opens a new version. Never overwrites historical rows.")
    public PersonResponse updateProfile(
            @PathVariable UUID id, @Valid @RequestBody UpdatePersonProfileRequest req) {
        return personService.updateProfile(id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Activate or deactivate a Person")
    public PersonResponse setStatus(
            @PathVariable UUID id, @Valid @RequestBody PersonStatusRequest req) {
        return personService.setStatus(id, req.active());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERSON_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a Person")
    public void softDelete(@PathVariable UUID id) {
        personService.softDelete(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "Full profile version history for a Person, newest first")
    public List<PersonVersionResponse> listVersions(@PathVariable UUID id) {
        return personService.listVersions(id);
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "Per-section and overall profile readiness")
    public ReadinessResponse readiness(@PathVariable UUID id) {
        return readinessService.compute(id);
    }

    // -------- Contact --------

    @PutMapping("/{id}/contact")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Set the current contact channels; creates a new effective-dated row")
    public ContactResponse setContact(
            @PathVariable UUID id, @Valid @RequestBody SetContactRequest req) {
        return contactService.setContact(id, req);
    }

    @GetMapping("/{id}/contacts")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List every contact-history row for a Person")
    public List<ContactResponse> listContacts(@PathVariable UUID id) {
        return contactService.list(id);
    }

    // -------- Addresses --------

    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Add an address")
    public ResponseEntity<AddressResponse> addAddress(
            @PathVariable UUID id, @Valid @RequestBody AddAddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.add(id, req));
    }

    @GetMapping("/{id}/addresses")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List addresses; optionally filter by kind")
    public List<AddressResponse> listAddresses(
            @PathVariable UUID id, @RequestParam(required = false) AddressKind kind) {
        return addressService.list(id, kind);
    }

    @PatchMapping("/{id}/addresses/{addressId}/retire")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Retire an address by setting its effectiveTo")
    public AddressResponse retireAddress(
            @PathVariable UUID id,
            @PathVariable UUID addressId,
            @Valid @RequestBody RetireRequest req) {
        return addressService.retire(id, addressId, req.effectiveTo());
    }

    // -------- Emergency contacts --------

    @PostMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Add an emergency contact")
    public ResponseEntity<EmergencyContactResponse> addEmergency(
            @PathVariable UUID id, @Valid @RequestBody AddEmergencyContactRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(emergencyService.add(id, req));
    }

    @GetMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List emergency contacts in priority order")
    public List<EmergencyContactResponse> listEmergency(@PathVariable UUID id) {
        return emergencyService.list(id);
    }

    @DeleteMapping("/{id}/emergency-contacts/{contactId}")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an emergency contact")
    public void deleteEmergency(@PathVariable UUID id, @PathVariable UUID contactId) {
        emergencyService.delete(id, contactId);
    }

    // -------- Family --------

    @PostMapping("/{id}/family")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Add a family member")
    public ResponseEntity<FamilyMemberResponse> addFamily(
            @PathVariable UUID id, @Valid @RequestBody AddFamilyMemberRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(familyService.add(id, req));
    }

    @GetMapping("/{id}/family")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List family members")
    public List<FamilyMemberResponse> listFamily(@PathVariable UUID id) {
        return familyService.list(id);
    }

    @DeleteMapping("/{id}/family/{memberId}")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a family member")
    public void deleteFamily(@PathVariable UUID id, @PathVariable UUID memberId) {
        familyService.delete(id, memberId);
    }

    // -------- Education --------

    @PostMapping("/{id}/education")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Add an education record")
    public ResponseEntity<EducationResponse> addEducation(
            @PathVariable UUID id, @Valid @RequestBody AddEducationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(educationService.add(id, req));
    }

    @GetMapping("/{id}/education")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List education records")
    public List<EducationResponse> listEducation(@PathVariable UUID id) {
        return educationService.list(id);
    }

    @DeleteMapping("/{id}/education/{educationId}")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an education record")
    public void deleteEducation(@PathVariable UUID id, @PathVariable UUID educationId) {
        educationService.delete(id, educationId);
    }

    // -------- Identity documents --------

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Add an identity document")
    public ResponseEntity<IdentityDocumentResponse> addDocument(
            @PathVariable UUID id, @Valid @RequestBody AddIdentityDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.add(id, req));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List identity documents; optionally filter by kind")
    public List<IdentityDocumentResponse> listDocuments(
            @PathVariable UUID id, @RequestParam(required = false) IdentityDocumentKind kind) {
        return documentService.list(id, kind);
    }

    @PatchMapping("/{id}/documents/{documentId}/retire")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Retire an identity document by setting its effectiveTo")
    public IdentityDocumentResponse retireDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody RetireRequest req) {
        return documentService.retire(id, documentId, req.effectiveTo());
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an identity document")
    public void deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        documentService.delete(id, documentId);
    }

    // -------- Duplicate detection --------

    @PostMapping("/duplicate-check")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(
            summary = "Run duplicate detection against candidate values without creating a Person")
    public DuplicateCheckResponse duplicateCheck(@Valid @RequestBody DuplicateCheckRequest req) {
        return duplicateDetectionService.check(req);
    }

    @GetMapping("/duplicate-rules")
    @PreAuthorize("hasAuthority('PERSON_READ')")
    @Operation(summary = "List configured duplicate-detection rules for a tenant")
    public List<DuplicateRuleResponse> listRules(@RequestParam(required = false) UUID tenantId) {
        return duplicateRuleService.list(tenantId);
    }

    @PutMapping("/duplicate-rules/{ruleId}")
    @PreAuthorize("hasAuthority('PERSON_WRITE')")
    @Operation(summary = "Enable / disable / reweight a duplicate-detection rule")
    public DuplicateRuleResponse updateRule(
            @PathVariable UUID ruleId, @Valid @RequestBody UpdateDuplicateRuleRequest req) {
        return duplicateRuleService.update(ruleId, req);
    }
}
