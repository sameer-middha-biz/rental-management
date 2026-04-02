# Holiday Rental Management Platform

## What This Project Is
A multi-tenant SaaS platform for holiday rental property management agencies. Think "Guesty" or "Lodgify" — property managers use it to manage listings, bookings, guests, payments, housekeeping, and OTA channel sync (Airbnb, Booking.com) from one dashboard.

## Business Context
- **Users:** Property management agencies, individual owners, housekeepers, guests
- **Multi-tenancy:** Each agency is a tenant. Shared database schema with `tenant_id` discriminator on all tenant-scoped tables. Data isolation is critical — every query must be tenant-scoped.
- **Subscription tiers:** Starter (5 properties), Pro (25), Agency (unlimited)
- **Revenue model:** SaaS subscription + Stripe payment processing for guest bookings
- **Deployment:** Coolify on Hostinger VPS (self-hosted). NOT AWS for MVP.
- **MVP success:** One paying agency managing 10+ properties

## Architecture Style
Modular Monolith — 17 business modules under `com.rental.pms.modules.*`. Modules communicate via service injection (for allowed dependencies) or Spring ApplicationEvents (for decoupled communication). No cross-module entity imports. Base package: `com.rental.pms`

## MVP Features (15 modules)
1. User Management & RBAC (6 roles: Super Admin → Guest)
2. Property Management (listings, photos, amenities)
3. Booking & Reservations (availability locking, dynamic pricing)
4. Guest Management (CRM, GDPR erasure)
5. Channel Management (Airbnb/Booking.com iCal sync)
6. Payments & Financial (Stripe, invoices, owner statements)
7. Subscription & Billing (plan enforcement)
8. Housekeeping (auto-created on checkout)
9. Maintenance & Issue Tracking
10. Reporting & Analytics
11. Owner Portal (read-only view for property owners)
12. Direct Booking Website
13. Search, Filtering & Navigation
14. Audit Trail & Activity Log
15. Notifications & Alerts (email, in-app, SMS)

For full MVP scope: docs/MVP_SCOPE.md
For full architecture: ARCHITECTURE.md

## Tech Stack
Java 21 | Spring Boot 3.x | PostgreSQL 16 | Redis 7 | Maven

## Critical Rules
- READ docs/CODING_STANDARDS.md before writing any code
- READ docs/TESTING_GUIDELINES.md before writing any tests
- ALWAYS generate unit tests alongside service/controller code
- NO cross-module entity/repository imports (use UUIDs + DTOs)
- ALL tenant-scoped entities extend BaseEntity
- ALL controller methods need @PreAuthorize
- @Transactional at Service layer ONLY
- Java records for ALL DTOs and events
- AssertJ for ALL test assertions (not JUnit assertEquals)
- PostgreSQL Testcontainers for integration tests (not H2)
- Flyway for ALL database schema changes (no manual DDL)
- MapStruct for ALL entity-DTO mapping
- Lombok @RequiredArgsConstructor for constructor injection
- @Enumerated(EnumType.STRING) for ALL enums (never ordinals)

## Architecture Reference
- Full spec: ARCHITECTURE.md
- Module dependencies: ARCHITECTURE.md lines 165-186
- Coding standards: docs/CODING_STANDARDS.md
- Testing guidelines: docs/TESTING_GUIDELINES.md
- Local setup: docs/LOCAL_SETUP.md
- MVP scope: docs/MVP_SCOPE.md

## Build Commands
- Compile: mvn clean compile
- Unit tests: mvn test
- All tests: mvn clean verify (requires Docker for Testcontainers)
- Run app: mvn spring-boot:run -Dspring-boot.run.profiles=local

## Task Files
Each phase has a detailed implementation plan in docs/tasks/:
- docs/tasks/01-project-setup-and-shared-kernel.md
- docs/tasks/02-security-and-auth-foundation.md
- docs/tasks/03-tenant-and-user-modules.md
- docs/tasks/04-property-and-subscription-modules.md
- docs/tasks/05-guest-and-booking-modules.md
- docs/tasks/06-payment-and-channel-sync.md
- docs/tasks/07-operations-housekeeping-and-maintenance.md
- docs/tasks/08-cross-cutting-features-and-ancillary-modules.md

## Phase Status
- [x] Phase 1: Project Setup & Shared Kernel
- [x] Phase 2: Security & Auth Foundation
- [x] Phase 3: Tenant & User Modules
- [ ] Phase 4: Property & Subscription Modules
- [ ] Phase 5: Guest & Booking Modules
- [ ] Phase 6: Payment & Channel Sync
- [ ] Phase 7: Housekeeping & Maintenance
- [ ] Phase 8: Cross-Cutting Features

## Deployment Documentation
- Full guide: docs/DEPLOYMENT.md
- **After completing each phase:** review docs/DEPLOYMENT.md and update the Phase Update Log table. Add any new environment variables introduced by that phase (new integrations, secrets, feature flags). Mark the phase row as Done.
