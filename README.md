# Holiday Rental Management Platform

Multi-tenant SaaS platform for holiday rental property management agencies. Built with Java 21, Spring Boot 3.x, PostgreSQL 16, and Redis. Deployed via Coolify on self-hosted VPS.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache | Redis 7 |
| Auth | JWT (RS256) + Spring Security |
| Payments | Stripe |
| File Storage | AWS S3 |
| Email | AWS SES + Thymeleaf |
| Deployment | Coolify (Docker, Traefik, Let's Encrypt) |
| CI/CD | GitHub Actions |

## Quick Start

See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) for prerequisites, Docker Compose setup, and build commands.

```bash
# Start infrastructure
docker compose up -d

# Build and run
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Documentation

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full architecture specification (modules, DB schema, deployment, NFRs) |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Java/Spring Boot coding standards, module boundaries, naming conventions |
| [docs/TESTING_GUIDELINES.md](docs/TESTING_GUIDELINES.md) | Testing strategy, multi-tenancy testing, coverage requirements |
| [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) | Local development environment setup |
| [docs/MVP_SCOPE.md](docs/MVP_SCOPE.md) | MVP feature scope (15 features) |
| [docs/POST_MVP_ROADMAP.md](docs/POST_MVP_ROADMAP.md) | Post-MVP feature roadmap |

## Implementation Phases

| Phase | Document | Modules |
|-------|----------|---------|
| 1 | [Project Setup & Shared Kernel](docs/tasks/01-project-setup-and-shared-kernel.md) | common/, config/ |
| 2 | [Security & Auth Foundation](docs/tasks/02-security-and-auth-foundation.md) | JWT, filters, encryption |
| 3 | [Tenant & User Modules](docs/tasks/03-tenant-and-user-modules.md) | Tenant, User, Auth |
| 4 | [Property & Subscription](docs/tasks/04-property-and-subscription-modules.md) | Property, Subscription |
| 5 | [Guest & Booking](docs/tasks/05-guest-and-booking-modules.md) | Guest, Booking, Pricing |
| 6 | [Payment & Channel Sync](docs/tasks/06-payment-and-channel-sync.md) | Payment, Channel/iCal |
| 7 | [Housekeeping & Maintenance](docs/tasks/07-operations-housekeeping-and-maintenance.md) | Housekeeping, Maintenance |
| 8 | [Cross-Cutting Features](docs/tasks/08-cross-cutting-features-and-ancillary-modules.md) | Audit, Notifications, Webhooks, Owner Portal |
