/**
 * Payroll module (WP-009).
 *
 * <p>Pay periods, pay components, employee compensation, payroll runs, and payslips. Runs consume
 * active {@link com.ewos.payroll.domain.EmployeeCompensation} records for a company and produce
 * immutable {@link com.ewos.payroll.domain.Payslip} snapshots per employee.
 *
 * <ul>
 *   <li>{@code .api} — REST controllers, request/response DTOs, mapper.
 *   <li>{@code .application} — use-case orchestration (PayComponentService, PayrollPeriodService,
 *       EmployeeCompensationService, PayrollRunService, PayslipService).
 *   <li>{@code .domain} — aggregates (PayComponent, PayrollPeriod, EmployeeCompensation,
 *       PayrollRun, Payslip), domain services (PayrollCalculator, PayrollPolicy), events.
 *   <li>{@code .infrastructure} — JPA repositories, Kafka publisher.
 * </ul>
 */
package com.ewos.payroll;
