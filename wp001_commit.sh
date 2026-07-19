#!/bin/bash
# ============================================================
# WP-001 FRONTEND FOUNDATION - Git Commit Script
# Run this script to commit WP-001 to your GitHub repository
# ============================================================

set -e

# CONFIGURATION - UPDATE THESE VARIABLES
GITHUB_ORG="YOUR_ORG"          # Replace with your GitHub org/username
REPO_NAME="ewos"                # Replace with your repository name
GITHUB_TOKEN="ghp_xxxxxxxx"     # Replace with your GitHub Personal Access Token

REPO_URL="https://${GITHUB_TOKEN}@github.com/${GITHUB_ORG}/${REPO_NAME}.git"
BRANCH_NAME="feature/wp-001-frontend-foundation"
SOURCE_DIR="/mnt/agents/output/wp-001-frontend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== WP-001 Frontend Foundation - Git Commit Script ===${NC}"
echo ""

# Verify source directory exists
if [ ! -d "$SOURCE_DIR" ]; then
    echo -e "${RED}ERROR: Source directory not found: $SOURCE_DIR${NC}"
    exit 1
fi

# Clone or navigate to repository
if [ ! -d "./${REPO_NAME}" ]; then
    echo -e "${YELLOW}Cloning repository...${NC}"
    git clone "$REPO_URL" "$REPO_NAME"
fi

cd "$REPO_NAME"

# Fetch latest changes
echo -e "${YELLOW}Fetching latest changes...${NC}"
git fetch origin

# Create and checkout feature branch
echo -e "${YELLOW}Creating feature branch: $BRANCH_NAME${NC}"
git checkout -b "$BRANCH_NAME" 2>/dev/null || git checkout "$BRANCH_NAME"

# Create frontend directory structure
mkdir -p frontend/src/{components/{auth,layout,ui,dashboard},hooks,pages,services,types,utils,context,routes}
mkdir -p frontend/public frontend/docs

# Copy all WP-001 files
echo -e "${YELLOW}Copying WP-001 files...${NC}"
cp -r "$SOURCE_DIR"/* ./frontend/

# Show what will be committed
echo ""
echo -e "${GREEN}Files to be committed:${NC}"
git status --short

# Stage all files
echo ""
echo -e "${YELLOW}Staging files...${NC}"
git add .

# Commit with detailed message
echo -e "${YELLOW}Creating commit...${NC}"
git commit -m "WP-001: Frontend Foundation - Complete Implementation

Framework & Build:
- React 19.0.0 + TypeScript 5.7
- Vite 6.0 build tool with code splitting
- Tailwind CSS 3.4 with custom design tokens
- PostCSS + Autoprefixer

State & Routing:
- TanStack Query v5 (React Query) with devtools
- React Router v7 with lazy loading
- Zustand ready for global state

UI Components (shadcn/ui pattern):
- Button, Input, Card, Avatar, Badge
- LoadingScreen with spinner

Layout Components:
- MainLayout: Sidebar + Header + Content shell
- Sidebar: Collapsible, mobile responsive, nested nav
- Header: Menu toggle, company switcher, notifications, profile
- Breadcrumb: Auto-generated from route path
- ThemeToggle: Light/Dark/System with persistence
- CompanySwitcher: Dropdown with company list
- NotificationPanel: Unread badge, mark read, delete
- UserProfileMenu: Avatar, name, role, logout

Authentication:
- JWT + Refresh Token flow
- AuthContext: login, logout, refresh, permissions, roles
- ProtectedRoute & PublicRoute guards
- Token auto-refresh 5min before expiry

API Layer:
- Axios with request/response interceptors
- Auth service: login, logout, refresh, me
- Company service: list, switch
- Notification service: CRUD operations

Hooks:
- useAuth, useTheme
- useCompany (list, current, switch)
- useNotifications (list, unread, mark read, delete)
- useBreadcrumb (auto-generated)

Utilities:
- cn() - clsx + tailwind-merge
- Date, currency, number formatters
- Constants: routes, nav items, storage keys, pagination

Types:
- User, Company, Role, Permission, Notification
- AuthTokens, LoginCredentials, ApiResponse
- BreadcrumbItem, PaginationParams

Pages:
- LoginPage: Email/password, validation, remember me
- DashboardPage: Stats cards, activity feed
- EmployeesPage, PayrollPage, AttendancePage, etc. (placeholders)
- NotFoundPage

Configuration:
- tsconfig.json with path aliases
- vite.config.ts with proxy, aliases, code splitting
- tailwind.config.js with custom colors, animations
- index.css with CSS variables for theming

Documentation:
- docs/WP-001-README.md

Total Files: 33
Total Lines: ~2,800"

# Push to remote
echo ""
echo -e "${YELLOW}Pushing to GitHub...${NC}"
git push -u origin "$BRANCH_NAME"

echo ""
echo -e "${GREEN}✅ WP-001 committed successfully!${NC}"
echo -e "${GREEN}Branch: $BRANCH_NAME${NC}"
echo -e "${GREEN}Create PR at: https://github.com/${GITHUB_ORG}/${REPO_NAME}/pull/new/${BRANCH_NAME}${NC}"
