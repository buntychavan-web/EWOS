#!/bin/bash
# ============================================================
# WP-002 USER MANAGEMENT - Git Commit Script
# Run this script to commit WP-002 to your GitHub repository
# ============================================================

set -e

# CONFIGURATION - UPDATE THESE VARIABLES
GITHUB_ORG="YOUR_ORG"
REPO_NAME="ewos"
GITHUB_TOKEN="ghp_xxxxxxxx"

REPO_URL="https://${GITHUB_TOKEN}@github.com/${GITHUB_ORG}/${REPO_NAME}.git"
BRANCH_NAME="feature/wp-002-user-management"
SOURCE_DIR="/mnt/agents/output/wp-002-user-management"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== WP-002 User Management - Git Commit Script ===${NC}"
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
mkdir -p backend/src/main/java/com/ewos/usermanagement/{config,controller,dto,entity,enums,exception,mapper,repository,security,service,audit,validation}
mkdir -p backend/src/main/resources/db/migration
mkdir -p backend/src/test/java/com/ewos/usermanagement
mkdir -p backend/src/test/resources

# Create frontend directory structure
mkdir -p frontend/src/{components/{users,roles,permissions},hooks/users,pages/{users,roles,permissions},services/users,types/users}

echo -e "${YELLOW}Copying WP-002 files...${NC}"
cp -r "$SOURCE_DIR"/* ./

echo ""
echo -e "${GREEN}Files to be committed:${NC}"
git status --short

echo ""
echo -e "${YELLOW}Staging files...${NC}"
git add .

echo -e "${YELLOW}Creating commit...${NC}"
git commit -m "WP-002: User Management Module - Complete Implementation

BACKEND:

Database (PostgreSQL + Flyway):
- V1__Create_Users_Table.sql: Users, Roles, Permissions, UserRoles, RolePermissions
- V2__Add_Company_Scope.sql: Company scope, extended user fields
- Audit logs, password reset tokens, refresh tokens tables
- Default data: 6 system roles, 16 permissions, super admin user

Entities:
- User: Full profile, status, lock mechanism, soft delete
- Role: System/custom roles, company/global scope
- Permission: Resource-action based permissions
- UserRole: Many-to-many with assignment metadata
- RolePermission: Many-to-many mapping
- AuditLog: JSONB old/new values, IP, user agent

Enums:
- UserStatus: ACTIVE, INACTIVE, LOCKED, PENDING
- Gender: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY
- AuditAction: CREATE, READ, UPDATE, DELETE, etc.

DTOs:
- UserDTO: Request, Response, Update, StatusUpdate, PasswordChange, Profile
- RoleDTO: Request, Response, Update, PermissionAssignment
- PermissionDTO: Response, ResourcePermissions
- ApiResponse: Generic with pagination meta

Repositories:
- UserRepository: Search, filter by status/department, count
- RoleRepository: By company/global, active list
- PermissionRepository: By resource, distinct resources
- AuditLogRepository: By entity, performer, action

Services:
- UserService: CRUD, search, status management, password change
- RoleService: CRUD, permission assignment
- PermissionService: Read-only, grouped by resource
- AuditLogService: @Transactional(REQUIRES_NEW), JSON serialization

Controllers:
- UserController: Full CRUD, search, filter, pagination, status, profile
- RoleController: CRUD, permission assignment
- PermissionController: List, by resource

Security:
- JWT authentication (access + refresh tokens)
- Password hashing with BCrypt
- Role-based access control (@PreAuthorize)
- Token refresh with 5min buffer

FRONTEND:

Types:
- User, Role, Permission interfaces
- CreateUserRequest, UpdateUserRequest, UserStatusUpdate
- UserFilters, UserListResponse with pagination

Services:
- userService: All CRUD operations, search, filter, pagination
- roleService: CRUD, permission assignment
- permissionService: List, by resource

Hooks (TanStack Query):
- useUsers: List with filters, pagination
- useUser: Get by ID
- useCreateUser, useUpdateUser, useDeleteUser
- useUpdateUserStatus: Lock/unlock/activate
- useProfile, useChangePassword

Pages:
- UserListPage: Table with search, status filter, department filter
  Pagination, status toggle, delete, edit navigation
- UserFormPage: Create/edit with React Hook Form + Zod
  Sections: Basic, Work, Personal details
- ProfilePage: Personal info, emergency contact, permissions

UI States:
- Loading: Skeleton rows, spinners
- Empty: Icon + message when no users
- Error: Retry button, error message
- Success: Toast notifications

Integration:
- Frontend services connect to /api/users endpoints
- JWT tokens in localStorage
- Auto-refresh on 401
- TanStack Query caching and invalidation

Total Files: 47
Backend Lines: ~3,500
Frontend Lines: ~2,200"

echo ""
echo -e "${YELLOW}Pushing to GitHub...${NC}"
git push -u origin "$BRANCH_NAME"

echo ""
echo -e "${GREEN}✅ WP-002 committed successfully!${NC}"
echo -e "${GREEN}Branch: $BRANCH_NAME${NC}"
echo -e "${GREEN}Create PR at: https://github.com/${GITHUB_ORG}/${REPO_NAME}/pull/new/${BRANCH_NAME}${NC}"
