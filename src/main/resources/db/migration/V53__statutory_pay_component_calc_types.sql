-- Sprint 24H-1 added five STATUTORY_* values to PayComponentCalculationType (PF/ESI/PT/LWF/TDS
-- resolve from the statutory engine, not from a compensation line). The CHECK constraints from
-- V14 only allowed 'FIXED'/'PERCENT_OF_BASIC' at the database level, so a component or payslip
-- line using a statutory calculation type was silently rejected on insert.

ALTER TABLE pay_components DROP CONSTRAINT ck_pay_components_calc_type;
ALTER TABLE pay_components ADD CONSTRAINT ck_pay_components_calc_type
    CHECK (calculation_type IN
        ('FIXED', 'PERCENT_OF_BASIC', 'STATUTORY_PF', 'STATUTORY_ESI', 'STATUTORY_PT',
         'STATUTORY_LWF', 'STATUTORY_TDS'));

ALTER TABLE payslip_lines DROP CONSTRAINT ck_payslip_lines_calc_type;
ALTER TABLE payslip_lines ADD CONSTRAINT ck_payslip_lines_calc_type
    CHECK (calculation_type IN
        ('FIXED', 'PERCENT_OF_BASIC', 'STATUTORY_PF', 'STATUTORY_ESI', 'STATUTORY_PT',
         'STATUTORY_LWF', 'STATUTORY_TDS'));
