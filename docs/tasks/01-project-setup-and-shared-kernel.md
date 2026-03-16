# Phase 1: Project Setup and Shared Kernel

## Prerequisites
- None (this is the foundation phase)

## Scope
- **In scope:** Maven project setup, global configuration, `common/` shared kernel, multi-tenancy infrastructure, exception handling, event bus
- **Out of scope:** All business modules (`modules/*`), security filters (Phase 2), REST endpoints

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V1.0__create_shedlock_table.sql` | `shedlock` | Required by ShedLock for distributed scheduler locking |

### Java Classes

**`pom.xml`**
- Spring Boot 3.4.x parent POM
- All dependencies from ARCHITECTURE.md Section 23 (Spring Boot starters, Flyway, PostgreSQL, JJWT, MapStruct, Bucket4j, ShedLock, Stripe, AWS S3, ical4j, Twilio, OpenPDF, Lombok)
- Test dependencies: spring-boot-starter-test, spring-security-test, Testcontainers (postgresql, localstack, redis)
- Maven compiler plugin with MapStruct + Lombok annotation processors
- Surefire plugin (unit tests: `*Test.java`)
- Failsafe plugin (integration tests: `*IntegrationTest.java`)
- JaCoCo plugin (80% coverage threshold)

**Global Configurations (`config/`)**
| Class | Responsibility |
|-------|---------------|
| `AsyncConfig.java` | Thread pool config + `TenantAwareTaskDecorator` (propagates TenantContext, SecurityContext, MDC to async threads) |
| `CacheConfig.java` | Redis cache manager with default TTL |
| `SchedulerConfig.java` | ShedLock configuration with JDBC lock provider |
| `SecurityConfig.java` | Placeholder (completed in Phase 2) |
| `S3Config.java` | AWS S3 client bean with configurable endpoint (LocalStack for local) |
| `OpenApiConfig.java` | Springdoc OpenAPI global config |
| `RateLimitConfig.java` | Placeholder (completed in Phase 2) |

**Common Entities (`common/entity/`)**
| Class | Responsibility |
|-------|---------------|
| `BaseEntity.java` | Abstract JPA entity with: `id` (UUID), `tenantId`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version`. Hibernate `@FilterDef` for `tenant_id`. `@PrePersist`/`@PreUpdate` for timestamps. |
| `TenantAware.java` | Interface: `UUID getTenantId()` |

**Multi-Tenancy (`common/multitenancy/`)**
| Class | Responsibility |
|-------|---------------|
| `TenantInterceptor.java` | JPA Entity Listener: `@PrePersist` sets `tenant_id` from `TenantContext`; `@PreUpdate` validates it hasn't changed |
| `TenantHibernateFilter.java` | Activates Hibernate `@Filter` on each request with current tenant UUID |

**Exceptions & Responses (`common/exception/`, `common/dto/`)**
| Class | Responsibility |
|-------|---------------|
| `BaseBusinessException.java` | Abstract RuntimeException with `errorCode` (String) and `httpStatus` (HttpStatus) |
| `ResourceNotFoundException.java` | 404 - resource not found |
| `ConflictException.java` | 409 - conflict (e.g., duplicate booking) |
| `TenantLimitExceededException.java` | 409 - subscription limit reached |
| `GlobalExceptionHandler.java` | `@ControllerAdvice` mapping exceptions to `ErrorResponse` |
| `ApiResponse.java` | Java record: generic success wrapper |
| `PageResponse.java` | Java record: `content`, `totalElements`, `totalPages`, `currentPage` |
| `ErrorResponse.java` | Java record: `timestamp`, `status`, `error`, `message`, `errorCode`, `path`, `fieldErrors` |

**Event Bus (`common/event/`)**
| Class | Responsibility |
|-------|---------------|
| `DomainEvent.java` | Base record/class for all domain events |
| `DomainEventPublisher.java` | Interface: `void publish(Object event)` |
| `SpringDomainEventPublisher.java` | MVP implementation using Spring `ApplicationEventPublisher` |

**Audit Infrastructure (`common/audit/`)**
| Class | Responsibility |
|-------|---------------|
| `AuditEvent.java` | Spring ApplicationEvent for audit trail |
| `AuditEventPublisher.java` | Convenience publisher for audit events |

**Utilities (`common/util/`)**
| Class | Responsibility |
|-------|---------------|
| `SlugGenerator.java` | Generates URL-friendly slugs from names |
| `MoneyUtil.java` | BIGINT (minor units) <> BigDecimal conversions |
| `Constants.java` | Shared constants: DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, CACHE_TTL_MINUTES |

**Application Properties**
| File | Purpose |
|------|---------|
| `application.yml` | Common config (HikariCP, Flyway, Actuator, Jackson) |
| `application-local.yml` | Local dev (Docker Compose URLs, debug logging) |
| `application-test.yml` | Test profile (Testcontainers JDBC URL) |
| `application-prod.yml` | Production (env var references, warn logging, Swagger disabled) |

**Docker**
| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build: `maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-jammy` |
| `docker-compose.yml` | PostgreSQL 16, Redis 7, LocalStack, MailHog |
| `.dockerignore` | Exclude target/, .git/, .env* |

### Domain Events
- None published (no business modules yet)

### Services
- None (only infrastructure/config in this phase)

## Acceptance Criteria
1. `mvn clean compile` succeeds with zero errors and zero warnings
2. Application starts with `local` profile when Docker Compose services are running
3. Flyway runs `V1.0__create_shedlock_table.sql` on startup
4. `/actuator/health` returns HTTP 200 with `{"status":"UP"}`
5. Swagger UI loads at `/swagger-ui.html` on local profile
6. `BaseEntity` Hibernate filter is defined with `tenant_id` parameter
7. `TenantInterceptor` sets `tenant_id` on `@PrePersist` and rejects changes on `@PreUpdate`
8. `GlobalExceptionHandler` returns correct `ErrorResponse` JSON for `ResourceNotFoundException` (404), `ConflictException` (409)
9. `SpringDomainEventPublisher` delegates to `ApplicationEventPublisher`
10. `TenantAwareTaskDecorator` propagates `TenantContext`, `SecurityContext`, and `MDC` to async threads

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `GlobalExceptionHandlerTest` | Unit | Each exception type maps to correct HTTP status and error code |
| `TenantInterceptorTest` | Unit | Sets tenant_id on new entity; rejects tenant_id change on update |
| `SpringDomainEventPublisherTest` | Unit | Delegates to ApplicationEventPublisher |
| `TenantAwareTaskDecoratorTest` | Unit | MDC and TenantContext propagated to decorated Runnable |
| `SlugGeneratorTest` | Unit | Generates valid slugs, handles special characters |
| `MoneyUtilTest` | Unit | Correct BIGINT <> BigDecimal conversion |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Application starts cleanly with `local` profile
- Docker Compose services start without errors
- No compiler warnings
- Code follows `docs/CODING_STANDARDS.md`
