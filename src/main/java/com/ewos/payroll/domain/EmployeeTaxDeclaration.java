package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * One employee's income-tax declaration for one fiscal year — the inputs {@link
 * com.ewos.payroll.application.IncomeTaxCalculationService} needs beyond the payslip itself
 * (previous-employer/other income, house-property loss, HRA/LTA exemption inputs, Chapter VI-A) —
 * and, on the same row, the running year-to-date accumulators the monthly TDS projection reads and
 * updates after every payroll run: {@code ytdTaxableSalary}, {@code ytdHraReceived}, {@code
 * ytdTdsDeducted}, {@code ytdProfessionalTaxPaid}.
 */
@Entity
@Table(name = "employee_tax_declarations")
@SQLDelete(
        sql =
                "UPDATE employee_tax_declarations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class EmployeeTaxDeclaration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "fiscal_year", nullable = false, length = 16)
    private String fiscalYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 16)
    private TaxRegime regime = TaxRegime.NEW;

    @Column(name = "previous_employer_income", nullable = false, precision = 18, scale = 4)
    private BigDecimal previousEmployerIncome = BigDecimal.ZERO;

    @Column(name = "other_income", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherIncome = BigDecimal.ZERO;

    @Column(name = "house_property_loss", nullable = false, precision = 18, scale = 4)
    private BigDecimal housePropertyLoss = BigDecimal.ZERO;

    @Column(name = "chapter_via_declared_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal chapterViaDeclaredAmount = BigDecimal.ZERO;

    @Column(name = "rent_paid_annual", nullable = false, precision = 18, scale = 4)
    private BigDecimal rentPaidAnnual = BigDecimal.ZERO;

    @Column(name = "is_metro_city", nullable = false)
    private boolean metroCity;

    @Column(name = "lta_exemption_declared", nullable = false, precision = 18, scale = 4)
    private BigDecimal ltaExemptionDeclared = BigDecimal.ZERO;

    @Column(name = "ytd_taxable_salary", nullable = false, precision = 18, scale = 4)
    private BigDecimal ytdTaxableSalary = BigDecimal.ZERO;

    @Column(name = "ytd_hra_received", nullable = false, precision = 18, scale = 4)
    private BigDecimal ytdHraReceived = BigDecimal.ZERO;

    @Column(name = "ytd_tds_deducted", nullable = false, precision = 18, scale = 4)
    private BigDecimal ytdTdsDeducted = BigDecimal.ZERO;

    @Column(name = "ytd_professional_tax_paid", nullable = false, precision = 18, scale = 4)
    private BigDecimal ytdProfessionalTaxPaid = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(String fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public TaxRegime getRegime() {
        return regime;
    }

    public void setRegime(TaxRegime regime) {
        this.regime = regime;
    }

    public BigDecimal getPreviousEmployerIncome() {
        return previousEmployerIncome;
    }

    public void setPreviousEmployerIncome(BigDecimal previousEmployerIncome) {
        this.previousEmployerIncome = previousEmployerIncome;
    }

    public BigDecimal getOtherIncome() {
        return otherIncome;
    }

    public void setOtherIncome(BigDecimal otherIncome) {
        this.otherIncome = otherIncome;
    }

    public BigDecimal getHousePropertyLoss() {
        return housePropertyLoss;
    }

    public void setHousePropertyLoss(BigDecimal housePropertyLoss) {
        this.housePropertyLoss = housePropertyLoss;
    }

    public BigDecimal getChapterViaDeclaredAmount() {
        return chapterViaDeclaredAmount;
    }

    public void setChapterViaDeclaredAmount(BigDecimal chapterViaDeclaredAmount) {
        this.chapterViaDeclaredAmount = chapterViaDeclaredAmount;
    }

    public BigDecimal getRentPaidAnnual() {
        return rentPaidAnnual;
    }

    public void setRentPaidAnnual(BigDecimal rentPaidAnnual) {
        this.rentPaidAnnual = rentPaidAnnual;
    }

    public boolean isMetroCity() {
        return metroCity;
    }

    public void setMetroCity(boolean metroCity) {
        this.metroCity = metroCity;
    }

    public BigDecimal getLtaExemptionDeclared() {
        return ltaExemptionDeclared;
    }

    public void setLtaExemptionDeclared(BigDecimal ltaExemptionDeclared) {
        this.ltaExemptionDeclared = ltaExemptionDeclared;
    }

    public BigDecimal getYtdTaxableSalary() {
        return ytdTaxableSalary;
    }

    public void setYtdTaxableSalary(BigDecimal ytdTaxableSalary) {
        this.ytdTaxableSalary = ytdTaxableSalary;
    }

    public BigDecimal getYtdHraReceived() {
        return ytdHraReceived;
    }

    public void setYtdHraReceived(BigDecimal ytdHraReceived) {
        this.ytdHraReceived = ytdHraReceived;
    }

    public BigDecimal getYtdTdsDeducted() {
        return ytdTdsDeducted;
    }

    public void setYtdTdsDeducted(BigDecimal ytdTdsDeducted) {
        this.ytdTdsDeducted = ytdTdsDeducted;
    }

    public BigDecimal getYtdProfessionalTaxPaid() {
        return ytdProfessionalTaxPaid;
    }

    public void setYtdProfessionalTaxPaid(BigDecimal ytdProfessionalTaxPaid) {
        this.ytdProfessionalTaxPaid = ytdProfessionalTaxPaid;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
