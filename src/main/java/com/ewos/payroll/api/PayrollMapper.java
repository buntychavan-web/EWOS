package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.ArrearResponse;
import com.ewos.payroll.api.dto.BankAdviceResponse;
import com.ewos.payroll.api.dto.CompensationLineResponse;
import com.ewos.payroll.api.dto.EmployeeBankAccountResponse;
import com.ewos.payroll.api.dto.EmployeeCompensationResponse;
import com.ewos.payroll.api.dto.EmployeePayrollProfileResponse;
import com.ewos.payroll.api.dto.EmployeeTaxDeclarationResponse;
import com.ewos.payroll.api.dto.EsiConfigurationResponse;
import com.ewos.payroll.api.dto.FinalSettlementResponse;
import com.ewos.payroll.api.dto.GratuityConfigurationResponse;
import com.ewos.payroll.api.dto.IncomeTaxPolicyResponse;
import com.ewos.payroll.api.dto.IncomeTaxSlabResponse;
import com.ewos.payroll.api.dto.IncomeTaxSurchargeSlabResponse;
import com.ewos.payroll.api.dto.LtaBlockClaimResponse;
import com.ewos.payroll.api.dto.LtaBlockConfigurationResponse;
import com.ewos.payroll.api.dto.LwfConfigurationResponse;
import com.ewos.payroll.api.dto.PayComponentResponse;
import com.ewos.payroll.api.dto.PayGroupResponse;
import com.ewos.payroll.api.dto.PaymentInstructionResponse;
import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.PayrollValidationReportResponse;
import com.ewos.payroll.api.dto.PayslipLineResponse;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.api.dto.PfConfigurationResponse;
import com.ewos.payroll.api.dto.ProfessionalTaxSlabResponse;
import com.ewos.payroll.api.dto.StatutoryChallanResponse;
import com.ewos.payroll.api.dto.StatutoryDeductionResponse;
import com.ewos.payroll.api.dto.StatutoryJurisdictionResponse;
import com.ewos.payroll.api.dto.StatutorySettingResponse;
import com.ewos.payroll.api.dto.TaxDeclarationProofResponse;
import com.ewos.payroll.api.dto.ValidationIssueResponse;
import com.ewos.payroll.domain.BankAdvice;
import com.ewos.payroll.domain.EmployeeBankAccount;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeeCompensationLine;
import com.ewos.payroll.domain.EmployeeLtaBlockClaim;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.EsiConfiguration;
import com.ewos.payroll.domain.FinalSettlement;
import com.ewos.payroll.domain.GratuityConfiguration;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.LtaBlockConfiguration;
import com.ewos.payroll.domain.LwfConfiguration;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.domain.PaymentInstruction;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipLineExplainer;
import com.ewos.payroll.domain.PfConfiguration;
import com.ewos.payroll.domain.ProfessionalTaxSlab;
import com.ewos.payroll.domain.StatutoryChallan;
import com.ewos.payroll.domain.StatutoryDeduction;
import com.ewos.payroll.domain.StatutoryJurisdiction;
import com.ewos.payroll.domain.StatutorySetting;
import com.ewos.payroll.domain.TaxDeclarationProof;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class PayrollMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    public PayComponentResponse toResponse(PayComponent c) {
        return new PayComponentResponse(
                c.getId(),
                c.getTenantId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getKind(),
                c.getCalculationType(),
                c.getDefaultAmount(),
                c.getDefaultPercentage(),
                c.isTaxable(),
                c.isRecurring(),
                c.isActive(),
                c.getSortOrder(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getVersionNo());
    }

    public PayrollPeriodResponse toResponse(PayrollPeriod p) {
        return new PayrollPeriodResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getFrequency(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getPayDate(),
                p.getStatus(),
                p.getLockedAt(),
                p.getLockedBy(),
                p.getClosedAt(),
                p.getClosedBy(),
                p.getVersionNo());
    }

    public EmployeeCompensationResponse toResponse(EmployeeCompensation c) {
        List<CompensationLineResponse> lines =
                c.getLines().stream().map(l -> toCompensationLineResponse(l)).toList();
        return new EmployeeCompensationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getEmployee() != null ? c.getEmployee().getId() : null,
                c.getPayGroup() != null ? c.getPayGroup().getId() : null,
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.getFrequency(),
                c.getBasicSalary(),
                c.getCurrency(),
                c.getNotes(),
                c.isActive(),
                lines,
                c.getVersionNo());
    }

    public PayrollRunResponse toResponse(PayrollRun r) {
        return new PayrollRunResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getPayrollPeriod() != null ? r.getPayrollPeriod().getId() : null,
                r.getStatus(),
                r.getRunType(),
                r.getStartedAt(),
                r.getStartedBy(),
                r.getCompletedAt(),
                r.getFinalizedAt(),
                r.getFinalizedBy(),
                r.getFrozenAt(),
                r.getFrozenBy(),
                r.getFailedAt(),
                r.getFailureReason(),
                r.getEmployeesProcessed(),
                r.getTotalGross(),
                r.getTotalDeductions(),
                r.getTotalNet(),
                r.getValidationReportJson(),
                r.getVersionNo());
    }

    public PayslipResponse toResponse(Payslip p) {
        List<PayslipLineResponse> lines =
                p.getLines().stream().map(l -> toPayslipLineResponse(l)).toList();
        return new PayslipResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getPayrollRun() != null ? p.getPayrollRun().getId() : null,
                p.getPayrollPeriod() != null ? p.getPayrollPeriod().getId() : null,
                p.getEmployee() != null ? p.getEmployee().getId() : null,
                p.getEmployeeNumberSnapshot(),
                p.getEmployeeNameSnapshot(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getPayDate(),
                p.getCurrency(),
                p.getGrossAmount(),
                p.getDeductionsAmount(),
                p.getNetAmount(),
                p.getLopDays(),
                p.getBasicEffective(),
                p.getStatus(),
                p.getFinalizedAt(),
                lines,
                p.getVersionNo());
    }

    private static CompensationLineResponse toCompensationLineResponse(EmployeeCompensationLine l) {
        return new CompensationLineResponse(
                l.getId(),
                l.getPayComponent() != null ? l.getPayComponent().getId() : null,
                l.getPayComponent() != null ? l.getPayComponent().getCode() : null,
                l.getAmount(),
                l.getPercentage());
    }

    public PayGroupResponse toResponse(PayGroup g) {
        return new PayGroupResponse(
                g.getId(),
                g.getTenantId(),
                g.getCompanyId(),
                g.getCode(),
                g.getName(),
                g.getDescription(),
                g.getFrequency(),
                g.getCurrency(),
                g.getPayDayOfMonth(),
                g.isActive(),
                g.getVersionNo());
    }

    public StatutorySettingResponse toResponse(StatutorySetting s) {
        return new StatutorySettingResponse(
                s.getId(),
                s.getTenantId(),
                s.getJurisdiction(),
                s.getCode(),
                s.getName(),
                s.getDescription(),
                s.getValueNumeric(),
                s.getValueString(),
                s.getEffectiveFrom(),
                s.getEffectiveTo(),
                s.isActive(),
                s.getVersionNo());
    }

    public EmployeeBankAccountResponse toResponse(EmployeeBankAccount b) {
        return new EmployeeBankAccountResponse(
                b.getId(),
                b.getTenantId(),
                b.getCompanyId(),
                b.getEmployee() != null ? b.getEmployee().getId() : null,
                b.getBankName(),
                b.getBranch(),
                b.getAccountHolderName(),
                b.getAccountNumberMasked(),
                b.getRoutingCode(),
                b.getSwiftBic(),
                b.getCurrency(),
                b.getCountryCode(),
                b.isPrimary(),
                b.isActive(),
                b.getVersionNo());
    }

    public EmployeePayrollProfileResponse toResponse(EmployeePayrollProfile p) {
        return new EmployeePayrollProfileResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getEmployee() != null ? p.getEmployee().getId() : null,
                p.getPayGroup() != null ? p.getPayGroup().getId() : null,
                p.getTaxRegime(),
                p.getCountryCode(),
                p.getStateCode(),
                p.isInternationalWorker(),
                p.isVpfEnabled(),
                p.getVpfPercentage(),
                readIdentifiers(p.getStatutoryIdentifiersJson()),
                p.getEffectiveFrom(),
                p.getEffectiveTo(),
                p.isActive(),
                p.getVersionNo());
    }

    public StatutoryDeductionResponse toResponse(StatutoryDeduction d) {
        return new StatutoryDeductionResponse(
                d.getId(),
                d.getTenantId(),
                d.getCompanyId(),
                d.getPayrollRun() != null ? d.getPayrollRun().getId() : null,
                d.getPayslip() != null ? d.getPayslip().getId() : null,
                d.getEmployee() != null ? d.getEmployee().getId() : null,
                d.getJurisdiction(),
                d.getCode(),
                d.getPeriodMonth(),
                d.getTaxableBase(),
                d.getEmployeeContribution(),
                d.getEmployerContribution(),
                d.getTotalAmount(),
                d.getCurrency(),
                d.getStatutoryChallan() != null ? d.getStatutoryChallan().getId() : null,
                d.getVersionNo());
    }

    public StatutoryChallanResponse toResponse(StatutoryChallan c) {
        return new StatutoryChallanResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getJurisdiction(),
                c.getCode(),
                c.getPeriodMonth(),
                c.getTotalEmployees(),
                c.getTotalTaxableBase(),
                c.getTotalEmployeeContribution(),
                c.getTotalEmployerContribution(),
                c.getTotalAmount(),
                c.getCurrency(),
                c.getStatus(),
                c.getFiledAt(),
                c.getFiledBy(),
                c.getFilingReference(),
                c.getPaidAt(),
                c.getPaidBy(),
                c.getPaymentReference(),
                c.getNotes(),
                c.getVersionNo());
    }

    public BankAdviceResponse toResponse(BankAdvice a) {
        List<PaymentInstructionResponse> lines =
                a.getInstructions().stream().map(PayrollMapper::toResponseInner).toList();
        return new BankAdviceResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getPayrollRun() != null ? a.getPayrollRun().getId() : null,
                a.getAdviceNumber(),
                a.getAdviceDate(),
                a.getCurrency(),
                a.getFileFormat(),
                a.getTotalCount(),
                a.getTotalAmount(),
                a.getStatus(),
                a.getGeneratedAt(),
                a.getGeneratedBy(),
                a.getAcknowledgedAt(),
                a.getAcknowledgedBy(),
                a.getSettledAt(),
                a.getNotes(),
                lines,
                a.getVersionNo());
    }

    public PaymentInstructionResponse toResponse(PaymentInstruction p) {
        return toResponseInner(p);
    }

    private static PaymentInstructionResponse toResponseInner(PaymentInstruction p) {
        return new PaymentInstructionResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getBankAdvice() != null ? p.getBankAdvice().getId() : null,
                p.getPayslip() != null ? p.getPayslip().getId() : null,
                p.getEmployee() != null ? p.getEmployee().getId() : null,
                p.getEmployeeBankAccount() != null ? p.getEmployeeBankAccount().getId() : null,
                p.getBankNameSnapshot(),
                p.getAccountHolderSnapshot(),
                p.getAccountNumberMasked(),
                p.getRoutingCodeSnapshot(),
                p.getSwiftBicSnapshot(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getSettlementReference(),
                p.getSettledAt(),
                p.getFailureReason(),
                p.getVersionNo());
    }

    public FinalSettlementResponse toResponse(FinalSettlement s) {
        return new FinalSettlementResponse(
                s.getId(),
                s.getTenantId(),
                s.getCompanyId(),
                s.getEmployee() != null ? s.getEmployee().getId() : null,
                s.getTerminationDate(),
                s.getLastWorkingDate(),
                s.getUnusedLeaveDays(),
                s.getEncashmentAmount(),
                s.getGratuityAmount(),
                s.getGratuityCalculatedAmount(),
                s.isGratuityOverridden(),
                s.getGratuityOverrideReason(),
                s.isGratuityEligibilityWaived(),
                s.getGratuityWaiverReason(),
                s.getNoticePayRecovery(),
                s.getNoticePayReceivable(),
                s.getOtherEarnings(),
                s.getOtherDeductions(),
                s.getCurrency(),
                s.getStatus(),
                s.getApprovedAt(),
                s.getApprovedBy(),
                s.getSettledAt(),
                s.getSettledBy(),
                s.getSettlementRun() != null ? s.getSettlementRun().getId() : null,
                s.getNotes(),
                s.getResignationId(),
                s.getVersionNo());
    }

    public ArrearResponse toResponse(PayrollArrear a) {
        return new ArrearResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getEmployee() != null ? a.getEmployee().getId() : null,
                a.getPayrollRun() != null ? a.getPayrollRun().getId() : null,
                a.getReasonCode(),
                a.getDescription(),
                a.getAmount(),
                a.getKind(),
                a.getForPeriodStart(),
                a.getForPeriodEnd(),
                a.isApplied(),
                a.getAppliedAt(),
                a.getBulkUploadBatchId(),
                a.getVersionNo());
    }

    public PayrollValidationReportResponse toResponse(
            UUID tenantId,
            UUID companyId,
            UUID payrollPeriodId,
            int employeeCount,
            PayrollValidationReport report) {
        return new PayrollValidationReportResponse(
                tenantId,
                companyId,
                payrollPeriodId,
                report.isRunnable(),
                employeeCount,
                report.blockers().stream().map(i -> toIssue(i)).toList(),
                report.warnings().stream().map(i -> toIssue(i)).toList());
    }

    private static ValidationIssueResponse toIssue(PayrollValidationReport.Issue i) {
        return new ValidationIssueResponse(i.employeeId(), i.employeeName(), i.code(), i.message());
    }

    public static String writeIdentifiers(Map<String, String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return "{}";
        }
        try {
            return JSON.writeValueAsString(identifiers);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize statutory identifiers: " + e.getMessage(), e);
        }
    }

    public static Map<String, String> readIdentifiers(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return JSON.readValue(json, STRING_MAP);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    public PfConfigurationResponse toResponse(PfConfiguration c) {
        return new PfConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getWageCeiling(),
                c.getEpsWageCeiling(),
                c.getEmployeeRatePct(),
                c.getEmployerPfRatePct(),
                c.getEpsRatePct(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.isActive(),
                c.getVersionNo());
    }

    public EsiConfigurationResponse toResponse(EsiConfiguration c) {
        return new EsiConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getWageThreshold(),
                c.getEmployeeRatePct(),
                c.getEmployerRatePct(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.isActive(),
                c.getVersionNo());
    }

    public GratuityConfigurationResponse toResponse(GratuityConfiguration c) {
        return new GratuityConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getStatutoryCeiling(),
                c.getRateNumerator(),
                c.getRateDenominator(),
                c.getMinYearsEligibility(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.isActive(),
                c.getVersionNo());
    }

    public ProfessionalTaxSlabResponse toResponse(ProfessionalTaxSlab s) {
        return new ProfessionalTaxSlabResponse(
                s.getId(),
                s.getTenantId(),
                s.getJurisdiction() != null ? s.getJurisdiction().getId() : null,
                s.getGender(),
                s.getMinMonthlyIncome(),
                s.getMaxMonthlyIncome(),
                s.getMonthlyTaxAmount(),
                s.getAnnualCapAmount(),
                s.getEffectiveFrom(),
                s.getEffectiveTo(),
                s.isActive(),
                s.getVersionNo());
    }

    public LwfConfigurationResponse toResponse(LwfConfiguration c) {
        return new LwfConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getJurisdiction() != null ? c.getJurisdiction().getId() : null,
                c.getEmployeeContribution(),
                c.getEmployerContribution(),
                c.getRemittanceMonths(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.isActive(),
                c.getVersionNo());
    }

    public IncomeTaxSlabResponse toResponse(IncomeTaxSlab s) {
        return new IncomeTaxSlabResponse(
                s.getId(),
                s.getTenantId(),
                s.getRegime(),
                s.getFiscalYear(),
                s.getMinIncome(),
                s.getMaxIncome(),
                s.getRatePct(),
                s.isActive(),
                s.getVersionNo());
    }

    public IncomeTaxPolicyResponse toResponse(IncomeTaxPolicy p) {
        return new IncomeTaxPolicyResponse(
                p.getId(),
                p.getTenantId(),
                p.getRegime(),
                p.getFiscalYear(),
                p.getRebateIncomeThreshold(),
                p.getRebateMaxAmount(),
                p.getCessRatePct(),
                p.getStandardDeduction(),
                p.getChapterViaMaxDeduction(),
                p.getHousePropertyLossCap(),
                p.isActive(),
                p.getVersionNo());
    }

    public IncomeTaxSurchargeSlabResponse toResponse(IncomeTaxSurchargeSlab s) {
        return new IncomeTaxSurchargeSlabResponse(
                s.getId(),
                s.getTenantId(),
                s.getRegime(),
                s.getFiscalYear(),
                s.getMinIncome(),
                s.getMaxIncome(),
                s.getSurchargeRatePct(),
                s.isActive(),
                s.getVersionNo());
    }

    public StatutoryJurisdictionResponse toResponse(StatutoryJurisdiction j) {
        return new StatutoryJurisdictionResponse(
                j.getId(), j.getCountryCode(), j.getStateCode(), j.getName(), j.isActive());
    }

    public EmployeeTaxDeclarationResponse toResponse(EmployeeTaxDeclaration d) {
        return new EmployeeTaxDeclarationResponse(
                d.getId(),
                d.getTenantId(),
                d.getCompanyId(),
                d.getEmployee() != null ? d.getEmployee().getId() : null,
                d.getFiscalYear(),
                d.getRegime(),
                d.getPreviousEmployerIncome(),
                d.getOtherIncome(),
                d.getHousePropertyLoss(),
                d.getChapterViaDeclaredAmount(),
                d.getRentPaidAnnual(),
                d.isMetroCity(),
                d.getLtaExemptionDeclared(),
                d.getYtdTaxableSalary(),
                d.getYtdHraReceived(),
                d.getYtdTdsDeducted(),
                d.getYtdProfessionalTaxPaid(),
                d.getYtdVariablePaymentTdsRecovered(),
                d.isActive(),
                d.getVersionNo());
    }

    private static PayslipLineResponse toPayslipLineResponse(PayslipLine l) {
        return new PayslipLineResponse(
                l.getId(),
                l.getPayComponent() != null ? l.getPayComponent().getId() : null,
                l.getComponentCodeSnapshot(),
                l.getComponentNameSnapshot(),
                l.getKind(),
                l.getCalculationType(),
                l.getAmount(),
                l.getPercentageApplied(),
                l.getSortOrder(),
                PayslipLineExplainer.explain(
                        l.getComponentNameSnapshot(),
                        l.getKind(),
                        l.getCalculationType(),
                        l.getPercentageApplied()));
    }

    public LtaBlockConfigurationResponse toResponse(LtaBlockConfiguration c) {
        return new LtaBlockConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getBlockDurationYears(),
                c.getAnchorBlockStartYear(),
                c.getMaxExemptClaimsPerBlock(),
                c.isCarryForwardEnabled(),
                c.getCarryForwardMaxClaims(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.isActive(),
                c.getNotes());
    }

    public LtaBlockClaimResponse toResponse(EmployeeLtaBlockClaim c) {
        return new LtaBlockClaimResponse(
                c.getId(),
                c.getEmployee() != null ? c.getEmployee().getId() : null,
                c.getBlockStartYear(),
                c.getBlockEndYear(),
                c.getClaimType(),
                c.getFiscalYear(),
                c.getClaimDate(),
                c.getLtaCreditedAmount(),
                c.getAmountClaimed(),
                c.getTaxFreeAmount(),
                c.getTaxableAmount(),
                c.isCarriedForwardFromPreviousBlock(),
                c.getNotes());
    }

    public com.ewos.payroll.api.dto.PayslipBrandingConfigurationResponse toResponse(
            com.ewos.payroll.domain.PayslipBrandingConfiguration c) {
        return new com.ewos.payroll.api.dto.PayslipBrandingConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getDisplayName(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getSupportEmail(),
                c.getFooterNote(),
                c.getLogoStorageUri(),
                c.getPasswordPolicy(),
                c.isActive());
    }

    public com.ewos.payroll.api.dto.KnowledgeDocumentResponse toResponse(
            com.ewos.payroll.domain.KnowledgeDocument d) {
        return new com.ewos.payroll.api.dto.KnowledgeDocumentResponse(
                d.getId(),
                d.getTenantId(),
                d.getCompanyId(),
                d.getDocumentFamilyId(),
                d.getVersionNumber(),
                d.getSourceType(),
                d.getTitle(),
                d.getReferenceNumber(),
                d.getSummary(),
                d.getTags(),
                d.getStorageUri(),
                d.getEffectiveFrom(),
                d.getEffectiveTo(),
                d.getStatus());
    }

    public TaxDeclarationProofResponse toResponse(TaxDeclarationProof p) {
        return new TaxDeclarationProofResponse(
                p.getId(),
                p.getEmployeeTaxDeclaration() != null
                        ? p.getEmployeeTaxDeclaration().getId()
                        : null,
                p.getProofType(),
                p.getFilename(),
                p.getMimeType(),
                p.getSizeBytes(),
                p.getStorageUri(),
                p.getNotes(),
                p.getUploadedAt());
    }
}
