# EWOS_MASTER_ARCHITECTURE_v1.0

**Status:** Architecture Frozen\
**Version:** 1.0\
**Date:** 2026-07-19

## Purpose

This is the frozen baseline architecture for the EWOS (Enterprise
Workforce Operating System). It is the authoritative reference for all
engineering work.

## Completed Phases

-   Phase 1 -- Enterprise Product & Functional Architecture (100%)
-   Phase 2 -- Enterprise Technical Architecture (100%)
-   Phase 3 -- Enterprise Delivery & Governance (100%)
-   Phase 4 -- Enterprise Development Execution (100%)

## Core Technology

### Backend

-   Java 21
-   Spring Boot 3.3
-   PostgreSQL
-   Flyway
-   Redis
-   Kafka
-   Maven

### Frontend

-   React 19
-   TypeScript
-   Vite
-   Tailwind CSS v4
-   TanStack Router
-   TanStack Query
-   React Hook Form
-   Zod
-   shadcn/ui

### Infrastructure

-   Kubernetes
-   Helm
-   Terraform
-   Argo CD
-   GitHub Actions
-   Prometheus
-   Grafana
-   Loki
-   Tempo
-   OpenTelemetry

## Engineering Principles

-   API First
-   Metadata Driven
-   Domain Driven Design
-   Clean Architecture
-   Hexagonal Architecture
-   CQRS where appropriate
-   Multi-tenant
-   Multi-company
-   AI Ready
-   Enterprise Scale

## Module Delivery Order

1.  Platform
2.  Core HR
3.  Attendance
4.  Leave
5.  Payroll
6.  Recruitment
7.  Talent
8.  Analytics
9.  AI Platform

## AI Responsibilities

-   Claude: Backend Engineering
-   Kimi: Frontend Engineering
-   Lovable: Full-stack Integration

## Freeze Rules

1.  No architectural redesign without approval.
2.  No customer-specific code.
3.  No hardcoded business rules.
4.  Every DB change via Flyway.
5.  Every API documented.
6.  Every feature includes automated tests.
7.  Human approval required for governed AI actions.

## Freeze Declaration

Version 1.0 is the official architecture baseline. All implementation
must conform to this document unless superseded by an approved future
version.
