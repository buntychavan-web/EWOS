package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class EmployeeContextTest {

    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE =
            "com.ewos.employee.currentEmployeeId";

    private final EmployeeContext employeeContext = new EmployeeContext();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentEmployeeIdReturnsEmployeePublishedOnTheRequestByTheJwtFilter() {
        UUID employeeId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, employeeId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(employeeContext.currentEmployeeId()).contains(employeeId);
    }

    @Test
    void currentEmployeeIdEmptyWhenNoneResolved() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(employeeContext.currentEmployeeId()).isEmpty();
    }

    @Test
    void currentEmployeeIdEmptyWhenNoRequestContextExists() {
        assertThat(employeeContext.currentEmployeeId()).isEmpty();
    }
}
