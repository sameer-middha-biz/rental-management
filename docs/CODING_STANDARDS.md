# Coding Standards & Guidelines

**Java Version:** 21 LTS
**Framework:** Spring Boot 3.x

## 1. General Java & Spring Boot Standards
* **Immutability:** Use `final` for all injected dependencies. Prefer constructor injection over `@Autowired` (use Lombok's `@RequiredArgsConstructor`).
* **Records:** Use Java `record` types for all DTOs (Request/Response objects) and Spring ApplicationEvents. Do not use classes for DTOs.
* **Optional:** Return `Optional<T>` from repositories when a single entity might not be found. Do not return nulls.
* **Exception Handling:** Do not throw generic `Exception` or `RuntimeException`. Throw specific domain exceptions (e.g., `ResourceNotFoundException`, `ConflictException`). Let the `GlobalExceptionHandler` translate these to HTTP 404, 409, etc.

## 2. API Design (REST)
* **Naming:** URLs must be lowercase, kebab-case, and pluralized (e.g., `/api/v1/cleaning-tasks`).
* **Responses:** Controller methods must return `ResponseEntity<T>`.
    * Creation (POST) must return HTTP 201 Created with the `Location` header.
    * Updates (PUT/PATCH) and fetches (GET) return HTTP 200 OK.
    * Deletions (DELETE) return HTTP 204 No Content.
* **Pagination:** Any endpoint returning a list must support pagination using Spring Data's `Pageable`. Return custom `PageResponse<T>` instead of raw Spring `Page<T>`.

## 3. Libraries & Boilerplate
* **Lombok:** Use `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` on JPA Entities carefully. Exclude lazy-loaded collections from `@ToString` and `@EqualsAndHashCode` to prevent `LazyInitializationException` and infinite loops.
* **MapStruct:** All Entity <> DTO mapping must be done via MapStruct interfaces annotated with `@Mapper(componentModel = "spring")`.

## 4. Transaction Boundaries
* Apply `@Transactional` exclusively at the Service layer, not at the Controller or Repository layer.
* Keep transactions as short as possible. Do not wrap external API calls (e.g., Stripe, AWS S3) inside a database transaction unless absolutely necessary.

---

## 5. Module Boundary Rules

This project is a **modular monolith**. Each module under `com.rental.pms.modules.{name}/` must be treated as an independent bounded context.

* **No cross-module entity/repository imports.** A module must never import another module's `entity/` or `repository/` packages directly.
* **Cross-module references use UUIDs.** When a module needs to reference an entity from another module, it uses the UUID identifier, never the entity object itself. DTOs are the exchange format between modules.
* **Service injection is allowed** between modules that have an explicit dependency (see table below). The dependent module injects the other module's Service class.
* **Asynchronous cross-module communication** uses the `DomainEventPublisher` interface (Spring ApplicationEvents in MVP). This is mandatory when the relationship would otherwise create a circular dependency.
* **The `common/` package is the only shared kernel.** It contains `BaseEntity`, `TenantAware`, exceptions, DTOs, event bus, and configuration. No business logic lives here.

### Module Dependency Table

| Module | Depends On |
|--------|-----------|
| `tenant` | none (foundation module) |
| `booking` | `property`, `guest`, `payment` |
| `channel` | `property`, `booking` |
| `payment` | `booking`, `tenant` |
| `subscription` | `tenant` |
| `housekeeping` | `booking`, `property`, `user` |
| `maintenance` | `property`, `user` |
| `reporting` | `booking`, `property`, `payment` |
| `owner` | `property`, `booking`, `payment`, `channel` |
| `directbooking` | `property`, `booking`, `payment`, `guest` |
| `search` | `property`, `booking`, `guest` |
| `notification` | all modules (leaf consumer) |
| `audit` | none (listens to Spring events) |
| `dataimport` | `property`, `booking`, `guest`, `channel` |

**No circular dependencies allowed.** If module A depends on module B, then B must not depend on A. Use domain events to break cycles (e.g., `booking` publishes events that `channel` listens to, avoiding a `booking` -> `channel` dependency).

---

## 6. Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| **Entity** | PascalCase, singular noun, no suffix | `Booking`, `CleaningTask` |
| **Repository** | `{Entity}Repository` | `BookingRepository` |
| **Service** | `{Domain}Service` (concrete class by default; interface only when needed for cross-module abstraction) | `BookingService` |
| **Controller** | `{Domain}Controller` | `BookingController` |
| **Request DTO** | `Create{Domain}Request`, `Update{Domain}Request` (Java records) | `CreateBookingRequest` |
| **Response DTO** | `{Domain}Response` (Java record) | `BookingResponse` |
| **Mapper** | `{Domain}Mapper` | `BookingMapper` |
| **Domain Event** | `{Domain}{Action}Event` (Java record) | `BookingCreatedEvent`, `BookingStatusChangedEvent` |
| **Flyway migration** | `V{version}__{description}.sql` | `V1.0__create_tenant_table.sql`, `V1.1__create_user_table.sql` |
| **Test class (unit)** | `{ClassName}Test` | `BookingServiceTest` |
| **Test class (integration)** | `{ClassName}IntegrationTest` | `BookingRepositoryIntegrationTest` |

### Package Structure (per module)

```
modules/{name}/
  controller/    # REST controllers
  service/       # Business logic
  repository/    # Spring Data JPA repositories
  entity/        # JPA entities + enums
  dto/           # Request/Response records
  mapper/        # MapStruct mapper interfaces
  event/         # Domain event records (if module publishes events)
  listener/      # Event listeners (if module consumes events)
```

---

## 7. Error Handling Strategy

### Error Response Format
All errors return the standard `ErrorResponse` record:
```java
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String errorCode,
    String path,
    List<FieldError> fieldErrors  // null unless validation error
) {}
```

### Error Code Format
Error codes follow: `{MODULE}.{ENTITY}.{ERROR_TYPE}`

Examples:
* `BOOKING.AVAILABILITY.CONFLICT` -- double-booking attempt
* `TENANT.LIMIT.EXCEEDED` -- property count exceeds subscription plan
* `AUTH.TOKEN.EXPIRED` -- JWT expired
* `AUTH.CREDENTIALS.INVALID` -- wrong email/password
* `PROPERTY.PHOTO.UPLOAD_FAILED` -- S3 upload error
* `GUEST.GDPR.ERASURE_FAILED` -- GDPR deletion error

### Exception Hierarchy
All domain exceptions extend `BaseBusinessException`:
```java
public abstract class BaseBusinessException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
}
```

The `GlobalExceptionHandler` maps each exception class to its HTTP status and error code.

---

## 8. Logging Standards

* **Framework:** SLF4J with Logback, JSON format output.
* **MDC (Mapped Diagnostic Context):** Every request must carry these fields:
  - `tenantId` -- from JWT claims
  - `userId` -- from JWT claims
  - `requestId` -- UUID generated per request in `TenantFilter`
* **MDC propagation:** `TenantFilter` populates MDC on the request thread. `TenantAwareTaskDecorator` copies MDC to `@Async` threads.
* **Log levels:**
  - `ERROR` -- Unrecoverable failures (DB down, Stripe webhook signature invalid)
  - `WARN` -- Recoverable issues (rate limit hit, iCal sync retry, stale cache)
  - `INFO` -- Business events (booking created, payment received, user registered)
  - `DEBUG` -- Internal flow tracing (query parameters, cache hit/miss)
* **PII protection:** Never log guest email, phone, or payment details at `INFO` or above. Mask sensitive fields in `DEBUG` logs (e.g., `email=j***@example.com`).

---

## 9. Security Annotation Patterns

* **Every controller method** must have a `@PreAuthorize` annotation. No controller endpoint should be accessible without explicit authorization.
* **Standard SpEL patterns:**
  - `@PreAuthorize("hasRole('SUPER_ADMIN')")` -- super admin only
  - `@PreAuthorize("hasAnyRole('AGENCY_ADMIN', 'PROPERTY_MANAGER')")` -- multiple roles
  - `@PreAuthorize("@permissionEvaluator.hasPermission(#id, 'PROPERTY', 'READ')")` -- resource-level
* **Tenant isolation is NOT enforced via `@PreAuthorize`.** It is enforced at the Hibernate filter level (`TenantHibernateFilter`) which automatically appends `WHERE tenant_id = :tenantId` to all queries on tenant-scoped entities.
* **Public endpoints** (registration, login, health check, direct booking) use `permitAll()` in `SecurityConfig` and do not need `@PreAuthorize`.

### Role Hierarchy
```
SUPER_ADMIN > AGENCY_ADMIN > PROPERTY_MANAGER > OWNER > HOUSEKEEPER > GUEST
```

---

## 10. Validation Standards

* **Bean Validation annotations** on DTO record constructor parameters:
  ```java
  public record CreateBookingRequest(
      @NotNull UUID propertyId,
      @NotNull @FutureOrPresent LocalDate checkIn,
      @NotNull @Future LocalDate checkOut,
      @Min(1) int guestCount
  ) {}
  ```
* **Custom validators** for domain rules (e.g., `@ValidDateRange` ensuring `checkOut > checkIn`).
* **`@Valid`** on all `@RequestBody` parameters in controller methods.
* **Validation errors** return HTTP 400 with `ErrorResponse` including a `fieldErrors` list:
  ```json
  {
    "errorCode": "VALIDATION.FIELD.INVALID",
    "fieldErrors": [
      { "field": "checkIn", "message": "must be a future or present date" }
    ]
  }
  ```

---

## 11. Constants and Enums

* **Enums** are placed in the module's `entity/` package (e.g., `BookingStatus`, `PaymentStatus`, `CleaningTaskStatus`).
* **Always use `@Enumerated(EnumType.STRING)`** -- never ordinals. Ordinals break when enum values are reordered.
* **Shared constants** go in `common/util/Constants.java`:
  ```java
  public final class Constants {
      public static final int DEFAULT_PAGE_SIZE = 20;
      public static final int MAX_PAGE_SIZE = 100;
      public static final long CACHE_TTL_MINUTES = 5;
      private Constants() {}
  }
  ```

---

## 12. No Hardcoding or Magic Values

* **No hardcoded configuration values** in Java source code. All environment-specific or tuneable values (URLs, ports, credentials, pool sizes, timeouts, feature toggles) must be externalised to `application.yml` / `application-{profile}.yml` and injected via `@Value` or `@ConfigurationProperties`.
* **No magic numbers or strings.** Numeric literals and string literals with business meaning must be extracted to named constants in `Constants.java`, enums, or configuration properties. Inline literals are only acceptable for truly universal values (e.g., `0`, `1`, `""`, `null` checks).
* **No default values in code that mask missing configuration.** If a configuration property is required for the application to function correctly, it must **not** have a fallback default in `@Value` annotations — the application should fail fast on startup if the value is missing. Defaults are only acceptable for optional/tuneable properties where a sensible fallback exists (e.g., page sizes, thread pool sizes).
* **Test profiles** may use inline values only inside `application-test.yml`, never in test Java source code.

---

## 13. OpenAPI / Swagger Documentation

* **Controllers:** Annotate with `@Tag(name = "Bookings", description = "Booking management endpoints")`.
* **Endpoints:** Annotate with `@Operation(summary = "Create a new booking")`.
* **DTOs:** Annotate fields with `@Schema(description = "...")` where the field name is not self-explanatory.
* **Swagger UI** is available at `/swagger-ui.html` in `local` and `dev` profiles only. Disabled in production via:
  ```yaml
  # application-prod.yml
  springdoc:
    swagger-ui:
      enabled: false
  ```
