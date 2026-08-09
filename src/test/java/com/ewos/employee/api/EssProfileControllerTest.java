package com.ewos.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EssProfileUpdateRequest;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.application.EmployeeService;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.exception.GlobalExceptionHandler;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Sprint 27C fix-round (F4) — controller-layer coverage for {@link EssProfileController} that needs
 * no Docker/Testcontainers: {@code MockMvcBuilders.standaloneSetup} wires the real controller
 * against mocked collaborators (the same {@code EmployeeService}/{@code EmployeeContext}/{@code
 * TenantContext}/{@code IdempotencyService} seams the controller is constructor-injected with) plus
 * the real {@link GlobalExceptionHandler}, so HTTP status codes, header/body validation, and
 * exception-to-response mapping are exercised through actual Spring MVC dispatch — not just a bare
 * Java method call. What this intentionally does not exercise: the real {@code
 * JwtAuthenticationFilter} / {@code TenantHeaderValidationFilter} chain (that needs the full Spring
 * context {@code AbstractIntegrationTest} provides, which requires Docker in this environment) —
 * {@code EmployeeContext}/{@code TenantContext} are mocked here exactly as the real filters would
 * have populated them.
 */
@ExtendWith(MockitoExtension.class)
class EssProfileControllerTest {

    private static final String URL = "/api/v1/self-service/me";
    private static final String HEADER = "Idempotency-Key";

    @Mock EmployeeService employees;
    @Mock EmployeeContext employeeContext;
    @Mock TenantContext tenantContext;
    @Mock IdempotencyService idempotency;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        EssProfileController controller =
                new EssProfileController(employees, employeeContext, tenantContext, idempotency);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    /**
     * Makes {@code idempotency.execute(...)} actually invoke the supplied action, like the real
     * bean.
     */
    @SuppressWarnings("unchecked")
    private void stubIdempotencyToRunTheAction() {
        when(idempotency.execute(any(), any(), any(), any(), eq(EmployeeResponse.class), any()))
                .thenAnswer(inv -> ((Supplier<EmployeeResponse>) inv.getArgument(5)).get());
    }

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString(
                new EssProfileUpdateRequest("new@example.com", "+1-555-0100", null, null, null));
    }

    @Test
    void updateMeReturns400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest());

        verify(employees, never()).updateMe(any(), any(), any());
    }

    @Test
    void updateMeReturns400WhenIdempotencyKeyHeaderIsBlank() throws Exception {
        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isBadRequest());

        verify(employees, never()).updateMe(any(), any(), any());
    }

    @Test
    void updateMeReturns400WhenPhoneFailsValidation() throws Exception {
        // @Valid rejects the request before the controller method body ever runs, so
        // tenantContext/employeeContext are never consulted for this case.
        String invalidBody =
                objectMapper.writeValueAsString(
                        new EssProfileUpdateRequest(
                                null, "not-a-phone-number!!", null, null, null));

        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(employees, never()).updateMe(any(), any(), any());
    }

    @Test
    void updateMeReturns404WhenNoEmployeeIsLinkedToTheCaller() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isNotFound());

        verify(employees, never()).updateMe(any(), any(), any());
    }

    @Test
    void updateMeReturns401WhenNoAuthenticatedUserIdIsResolvable() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(tenantContext.currentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isUnauthorized());

        verify(employees, never()).updateMe(any(), any(), any());
    }

    @Test
    void updateMeSucceedsAndReturnsTheUpdatedProfileScopedToTheCallersOwnEmployeeId()
            throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        EmployeeResponse response =
                new EmployeeResponse(
                        employeeId,
                        tenantId,
                        null,
                        null,
                        null,
                        "E1",
                        "Jane",
                        null,
                        "Doe",
                        "Jane Doe",
                        "jane@work.example",
                        "new@example.com",
                        "+1-555-0100",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L);
        when(employees.updateMe(eq(tenantId), eq(employeeId), any())).thenReturn(response);

        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.personalEmail").value("new@example.com"));

        // The employeeId passed to the service always comes from EmployeeContext, never from the
        // request body — EssProfileUpdateRequest structurally has no employeeId field to forge.
        verify(employees).updateMe(eq(tenantId), eq(employeeId), any());
    }

    @Test
    void updateMePropagatesA404FromTheServiceUnchanged() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(employees.updateMe(eq(tenantId), eq(employeeId), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));

        mockMvc.perform(
                        patch(URL)
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isNotFound());
    }
}
