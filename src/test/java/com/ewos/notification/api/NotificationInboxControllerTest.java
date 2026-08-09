package com.ewos.notification.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.notification.api.dto.NotificationPageResponse;
import com.ewos.notification.application.NotificationService;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.exception.GlobalExceptionHandler;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Sprint 27C fix-round (F4) — controller-layer coverage for {@link NotificationInboxController}
 * that needs no Docker/Testcontainers; same {@code MockMvcBuilders.standaloneSetup} approach as
 * {@code EssProfileControllerTest} — see that class's javadoc for what this does and does not
 * exercise.
 */
@ExtendWith(MockitoExtension.class)
class NotificationInboxControllerTest {

    private static final String BASE = "/api/v1/self-service/notifications";
    private static final String HEADER = "Idempotency-Key";

    @Mock NotificationService notifications;
    @Mock TenantContext tenantContext;
    @Mock IdempotencyService idempotency;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        NotificationInboxController controller =
                new NotificationInboxController(notifications, tenantContext, idempotency);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
    }

    @SuppressWarnings("unchecked")
    private void stubIdempotencyToRunTheAction() {
        when(idempotency.execute(any(), any(), any(), any(), eq(String.class), any()))
                .thenAnswer(inv -> ((Supplier<String>) inv.getArgument(5)).get());
    }

    @Test
    void listUsesTheDefaultLimitAndUnreadOnlyFalseWhenNeitherIsProvided() throws Exception {
        when(notifications.myPage(tenantId, false, null, 20))
                .thenReturn(new NotificationPageResponse(List.of(), null));

        mockMvc.perform(get(BASE)).andExpect(status().isOk());

        verify(notifications).myPage(tenantId, false, null, 20);
    }

    @Test
    void listPassesThroughUnreadOnlyAndCursor() throws Exception {
        when(notifications.myPage(tenantId, true, "cursor-1", 20))
                .thenReturn(new NotificationPageResponse(List.of(), null));

        mockMvc.perform(get(BASE).param("unreadOnly", "true").param("cursor", "cursor-1"))
                .andExpect(status().isOk());

        verify(notifications).myPage(tenantId, true, "cursor-1", 20);
    }

    @Test
    void listClampsAnOversizedLimitToTheMaximum() throws Exception {
        when(notifications.myPage(eq(tenantId), anyBoolean(), any(), eq(100)))
                .thenReturn(new NotificationPageResponse(List.of(), null));

        mockMvc.perform(get(BASE).param("limit", "500")).andExpect(status().isOk());

        verify(notifications).myPage(tenantId, false, null, 100);
    }

    @Test
    void listFallsBackToTheDefaultLimitWhenZeroOrNegativeIsRequested() throws Exception {
        when(notifications.myPage(eq(tenantId), anyBoolean(), any(), eq(20)))
                .thenReturn(new NotificationPageResponse(List.of(), null));

        mockMvc.perform(get(BASE).param("limit", "0")).andExpect(status().isOk());

        verify(notifications).myPage(tenantId, false, null, 20);
    }

    @Test
    void readMarksTheNotificationAndReturns204() throws Exception {
        mockMvc.perform(post(BASE + "/" + notificationId + "/read"))
                .andExpect(status().isNoContent());

        verify(notifications).markRead(tenantId, notificationId);
    }

    @Test
    void readPropagatesA404WhenTheNotificationIsNotOwnedByTheCaller() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "Notification not found"))
                .when(notifications)
                .markRead(tenantId, notificationId);

        mockMvc.perform(post(BASE + "/" + notificationId + "/read"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dismissReturns401WhenNoAuthenticatedUserIdIsResolvable() throws Exception {
        when(tenantContext.currentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post(BASE + "/" + notificationId + "/dismiss").header(HEADER, "key-1"))
                .andExpect(status().isUnauthorized());

        verify(notifications, never()).dismiss(any(), any());
    }

    @Test
    void dismissSucceedsWithAnIdempotencyKeyAndReturns204() throws Exception {
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();

        mockMvc.perform(post(BASE + "/" + notificationId + "/dismiss").header(HEADER, "key-1"))
                .andExpect(status().isNoContent());

        verify(notifications).dismiss(tenantId, notificationId);
        verify(idempotency)
                .execute(
                        eq(tenantId),
                        eq(actorId),
                        eq("self-service.notifications.dismiss"),
                        eq("key-1"),
                        eq(String.class),
                        any());
    }

    /**
     * Unlike {@code PATCH /self-service/me}, the PRD does not mark {@code Idempotency-Key} as
     * required for dismiss (only §4.5's profile update is explicitly "Required"); the controller
     * treats it as optional, and {@code IdempotencyService.execute} already treats a null/blank key
     * as "run the action directly, no idempotency tracking" — proven here end to end.
     */
    @Test
    void dismissStillWorksWithoutAnIdempotencyKeyHeaderSinceItIsOptionalHere() throws Exception {
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();

        mockMvc.perform(post(BASE + "/" + notificationId + "/dismiss"))
                .andExpect(status().isNoContent());

        verify(notifications).dismiss(tenantId, notificationId);
        verify(idempotency)
                .execute(
                        eq(tenantId),
                        eq(actorId),
                        eq("self-service.notifications.dismiss"),
                        isNull(),
                        eq(String.class),
                        any());
    }

    @Test
    void dismissPropagatesA404WhenTheNotificationIsNotOwnedByTheCaller() throws Exception {
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "Notification not found"))
                .when(notifications)
                .dismiss(tenantId, notificationId);

        mockMvc.perform(post(BASE + "/" + notificationId + "/dismiss").header(HEADER, "key-1"))
                .andExpect(status().isNotFound());
    }
}
