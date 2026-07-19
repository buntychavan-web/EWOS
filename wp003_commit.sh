#!/bin/bash
# ============================================================
# WP-003 ORGANIZATION SETUP - Git Commit Script
# Run this script to commit WP-003 to your GitHub repository
# ============================================================

set -e

# CONFIGURATION - UPDATE THESE VARIABLES
GITHUB_ORG="YOUR_ORG"
REPO_NAME="ewos"
GITHUB_TOKEN="ghp_xxxxxxxx"

REPO_URL="https://${GITHUB_TOKEN}@github.com/${GITHUB_ORG}/${REPO_NAME}.git"
BRANCH_NAME="feature/wp-003-organization-setup"
SOURCE_DIR="/mnt/agents/output/wp-003-organization-setup"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== WP-003 Organization Setup - Git Commit Script ===${NC}"
echo ""

if [ ! -d "$SOURCE_DIR" ]; then
    echo -e "${RED}ERROR: Source directory not found: $SOURCE_DIR${NC}"
    exit 1
fi

if [ ! -d "./${REPO_NAME}" ]; then
    echo -e "${YELLOW}Cloning repository...${NC}"
    git clone "$REPO_URL" "$REPO_NAME"
fi

cd "$REPO_NAME"

echo -e "${YELLOW}Fetching latest changes...${NC}"
git fetch origin

echo -e "${YELLOW}Creating feature branch: $BRANCH_NAME${NC}"
git checkout -b "$BRANCH_NAME" 2>/dev/null || git checkout "$BRANCH_NAME"

# Create backend directory structure
mkdir -p backend/src/main/java/com/ewos/organization/{config,controller,dto,entity,enums,exception,mapper,repository,service,audit,validation}
mkdir -p backend/src/main/resources/db/migration
mkdir -p backend/src/test/java/com/ewos/organization

# Create frontend directory structure
mkdir -p frontend/src/{components/{organization,company,branches,departments,designations},hooks/organization,pages/{organization,company,branches,departments,designations},services/organization,types/organization}

echo -e "${YELLOW}Copying WP-003 files...${NC}"
cp -r "$SOURCE_DIR"/* ./

echo ""
echo -e "${GREEN}Files to be committed:${NC}"
git status --short

echo ""
echo -e "${YELLOW}Staging files...${NC}"
git add .

echo -e "${YELLOW}Creating commit...${NC}"
git commit -m "WP-003: Organization Setup Module - Complete Implementation

BACKEND:

Database (PostgreSQL + Flyway):
- V1__Create_Organization_Tables.sql:
  * companies: Full registration, contact, address, fiscal, branding
  * branches: Multiple branch types, working hours, work days
  * departments: Hierarchical with materialized path
  * designations: Grade/level system, approval authority
  * company_settings: Payroll, attendance, leave, notifications, compliance
  * org_audit_logs: Organization-specific audit trail
- Default data: 1 company, 8 departments, 13 designations

Entities:
- Company: Registration, contact, address, fiscal, branding, status
- Branch: Type, working hours, timezone, active/default flags
- Department: Hierarchical (parent/children), path, cost center, HOD
- Designation: Grade, level, approver flag, approval limit, reports-to
- CompanySettings: Payroll cycle, attendance, leave, notifications, compliance

Enums:
- CompanyType: PRIVATE_LIMITED, PUBLIC_LIMITED, LLP, etc.
- CompanyStatus: ACTIVE, INACTIVE, SUSPENDED, DELETED
- BranchType: HEAD_OFFICE, BRANCH_OFFICE, etc.
- PayrollCycle: WEEKLY, BIWEEKLY, MONTHLY, etc.
- AttendanceTracking: MANUAL, BIOMETRIC, GPS, etc.

DTOs:
- CompanyDTO: Request, Response, Update, StatusUpdate, ListResponse
- BranchDTO: Request, Response, Update
- DepartmentDTO: Request, Response, Update, TreeResponse
- DesignationDTO: Request, Response, Update
- CompanySettingsDTO: Request, Response
- ApiResponse: Generic with pagination

Repositories:
- CompanyRepository: Search, by status, default, counts
- BranchRepository: By company, by type, default, search
- DepartmentRepository: Hierarchical, by company, children
- DesignationRepository: By company, approvers, search
- CompanySettingsRepository: By company ID

Services:
- CompanyService: CRUD, search, status, cache (Caffeine), audit
- BranchService: CRUD, by company, search
- DepartmentService: CRUD, hierarchical tree, reorder
- DesignationService: CRUD, by company, approvers
- CompanySettingsService: Get/update by company

Controllers:
- CompanyController: Full CRUD, search, status, default, active list
- BranchController: CRUD, by company, search
- DepartmentController: CRUD, tree, by company
- DesignationController: CRUD, by company, approvers
- CompanySettingsController: Get/update settings

Caching:
- Caffeine cache for companies
- @Cacheable, @CacheEvict annotations
- 10min expiry, 1000 max size

FRONTEND:

Types:
- Company, Branch, Department, Designation, CompanySettings
- CompanyType, CompanyStatus, BranchType, PayrollCycle, AttendanceTracking
- CompanyFilters, BranchFilters, DepartmentFilters, DesignationFilters

Services:
- companyService: All CRUD, search, settings
- branchService: CRUD, by company
- departmentService: CRUD, tree
- designationService: CRUD, by company

Hooks:
- useCompanies: List with filters, pagination
- useCompany: Get by ID
- useDefaultCompany, useActiveCompanies
- useCreateCompany, useUpdateCompany, useDeleteCompany
- useCompanySettings, useUpdateCompanySettings

Pages:
- CompanyListPage: Card grid, search, status/type filters
  Skeleton loading, empty state, pagination
- CompanyFormPage: Multi-tab (Basic, Registration, Address, Settings)
  React Hook Form + Zod validation
- CompanyDetailPage: Overview, registration, stats sidebar
- CompanySettingsPage: 5 tabs (Payroll, Attendance, Leave, Notifications, Compliance)

Navigation:
- Sidebar updated with Organization menu
- Companies, Branches, Departments, Designations

UI States:
- Loading: Skeleton cards, spinners
- Empty: Building icon + message
- Error: Retry button
- Success: Toast notifications

Integration:
- Frontend connects to /api/companies endpoints
- Reuses WP-001 auth, theme, layout components
- TanStack Query caching

Total Files: 47
Backend Lines: ~4,200
Frontend Lines: ~3,100"

echo ""
echo -e "${YELLOW}Pushing to GitHub...${NC}"
git push -u origin "$BRANCH_NAME"

echo ""
echo -e "${GREEN}✅ WP-003 committed successfully!${NC}"
echo -e "${GREEN}Branch: $BRANCH_NAME${NC}"
echo -e "${GREEN}Create PR at: https://github.com/${GITHUB_ORG}/${REPO_NAME}/pull/new/${BRANCH_NAME}${NC}"
