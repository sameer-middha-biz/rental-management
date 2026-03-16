# Testing Strategy & Guidelines

**Core Tools:** JUnit 5, Mockito, AssertJ, Testcontainers.

## 1. AI Code Generation Rule
For every Service or Controller generated, you MUST generate the corresponding Unit Test class in the same response. Do not wait for me to ask for the tests.

## 2. Unit Testing (Services & Mappers)
* **Framework:** Use JUnit 5 (`@ExtendWith(MockitoExtension.class)`).
* **Assertions:** Use AssertJ (`assertThat(...)`) exclusively. Do not use standard JUnit `assertEquals`.
* **Mocking:** Use `@Mock` for dependencies and `@InjectMocks` for the class under test.
* **Naming Convention:** Test method names should clearly state the scenario and expected outcome (e.g., `calculatePrice_WhenLastMinuteDiscountApplies_ShouldReduceTotalBy15Percent()`).
* **Event Verification:** When a service publishes an event (e.g., via `DomainEventPublisher`), the test MUST use `ArgumentCaptor` or `verify(...)` to ensure the correct event payload was published.

## 3. Integration Testing (Repositories & Controllers)
* **Database:** Use `@DataJpaTest` with PostgreSQL Testcontainers (`@Testcontainers`). Do not use H2 for integration testing to ensure native PostgreSQL features (like `tsvector` or `pg_advisory_xact_lock`) work correctly.
* **Controllers:** Use `@WebMvcTest` with `MockMvc`. Mock the underlying service layer. Verify HTTP status codes, JSON paths, and specific exception translations.
* **Security:** Use `@WithMockUser` to simulate authenticated requests and test RBAC / `@PreAuthorize` annotations.

## 4. Coverage Expectation
* Assume the project enforces an 80% line coverage and 100% domain logic coverage via JaCoCo. Ensure generated tests cover edge cases (e.g., missing tenant IDs, invalid states), not just the happy path.

---

## 5. Multi-Tenancy Test Strategy

Multi-tenancy isolation is the single most critical thing to test in this application. Every data leak between tenants is a security incident.

* **Every integration test** must set `TenantContext` with a valid tenant UUID before executing any database operation.
* **`TestTenantFixture` utility:** Create a shared test utility that provisions a test tenant (inserts into `tenants` table) and returns its UUID. Place in `src/test/java/com/rental/pms/common/fixture/TestTenantFixture.java`.
* **Mandatory isolation test per repository:** For every `@DataJpaTest` integration test class, include at least one test that:
  1. Creates data under Tenant A
  2. Switches `TenantContext` to Tenant B
  3. Queries the same repository
  4. Asserts **zero results** returned

  Example:
  ```java
  @Test
  void findAll_WhenDifferentTenant_ShouldReturnEmpty() {
      // Given: a property exists for Tenant A
      TenantContext.setTenantId(tenantA);
      propertyRepository.save(TestPropertyBuilder.aProperty().build());

      // When: querying as Tenant B
      TenantContext.setTenantId(tenantB);
      List<Property> results = propertyRepository.findAll();

      // Then: no cross-tenant data leaks
      assertThat(results).isEmpty();
  }
  ```
* **Teardown:** Always clear `TenantContext` in `@AfterEach` to prevent test pollution.

---

## 6. Test Data Strategy

* **Test Data Builders:** Create a `Test{Entity}Builder` per module using the Builder pattern with sensible defaults. Place in `src/test/java/com/rental/pms/modules/{module}/fixture/`.
  ```java
  public class TestBookingBuilder {
      private UUID propertyId = UUID.randomUUID();
      private UUID guestId = UUID.randomUUID();
      private LocalDate checkIn = LocalDate.now().plusDays(7);
      private LocalDate checkOut = LocalDate.now().plusDays(10);
      private BookingStatus status = BookingStatus.CONFIRMED;

      public static TestBookingBuilder aBooking() { return new TestBookingBuilder(); }
      public TestBookingBuilder withProperty(UUID id) { this.propertyId = id; return this; }
      public TestBookingBuilder withStatus(BookingStatus s) { this.status = s; return this; }
      public Booking build() { /* construct entity */ }
  }
  ```
* **SQL test data:** For complex integration test scenarios requiring multiple related entities, use `@Sql("/sql/test-data/{module}-setup.sql")` to load predefined datasets. Store SQL files in `src/test/resources/sql/test-data/`.
* **Never use random/production data** in tests. All test data must be deterministic and self-contained.

---

## 7. Integration Test Configuration

* **Class naming:** `{ClassName}IntegrationTest` (e.g., `BookingRepositoryIntegrationTest`, `AuthControllerIntegrationTest`).
* **Spring profile:** Use `@ActiveProfiles("test")` on all integration test classes.
* **`application-test.yml`:** Configure Testcontainers JDBC URL for automatic container lifecycle:
  ```yaml
  spring:
    datasource:
      url: jdbc:tc:postgresql:16:///pmsdb
    jpa:
      hibernate:
        ddl-auto: none   # Flyway handles schema
    flyway:
      enabled: true
  ```
* **Test independence:** Tests must not rely on execution order. Each test either:
  - Uses `@Transactional` (auto-rollback after each test), or
  - Explicitly cleans up data in `@AfterEach`
* **Testcontainers reuse:** Enable container reuse for faster local test runs:
  ```properties
  # ~/.testcontainers.properties
  testcontainers.reuse.enable=true
  ```

---

## 8. Event Testing

### Unit Tests (Service layer)
Use `ArgumentCaptor` to verify the exact event payload:
```java
@Test
void create_ShouldPublishBookingCreatedEvent() {
    // ... arrange & act ...
    ArgumentCaptor<BookingCreatedEvent> captor =
        ArgumentCaptor.forClass(BookingCreatedEvent.class);
    verify(eventPublisher).publish(captor.capture());

    BookingCreatedEvent event = captor.getValue();
    assertThat(event.bookingId()).isEqualTo(savedBooking.getId());
    assertThat(event.propertyId()).isEqualTo(propertyId);
}
```

### Integration Tests (End-to-end event flow)
Create a `@TestEventListener` component that captures published events into a list:
```java
@Component
@Profile("test")
public class TestEventCaptor {
    private final List<Object> capturedEvents = new CopyOnWriteArrayList<>();

    @EventListener
    public void capture(Object event) { capturedEvents.add(event); }

    public <T> List<T> eventsOfType(Class<T> type) {
        return capturedEvents.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() { capturedEvents.clear(); }
}
```
Assert: `assertThat(eventCaptor.eventsOfType(BookingCreatedEvent.class)).hasSize(1)`

---

## 9. API Contract Testing

* **JSON structure validation:** Controller tests (`@WebMvcTest`) must validate the full JSON response structure, not just HTTP status codes. Verify:
  - Required fields are present
  - Date formats are ISO-8601
  - Enum values are serialized as strings
  - Pagination wrapper (`PageResponse`) has `content`, `totalElements`, `totalPages`, `currentPage`
* **Error response validation:** Test that domain exceptions produce the expected `ErrorResponse` with correct `errorCode`:
  ```java
  mockMvc.perform(post("/api/v1/bookings").content(conflictingBooking))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.errorCode").value("BOOKING.AVAILABILITY.CONFLICT"));
  ```
* **OpenAPI spec generation:** Use `springdoc-openapi-starter-webmvc-api` to auto-generate the OpenAPI spec at build time. Consider adding a test that validates the generated spec is valid YAML/JSON.

---

## 10. Test Profiles and CI

### Maven Plugin Configuration
* **Surefire (unit tests):** Runs classes matching `*Test.java`
* **Failsafe (integration tests):** Runs classes matching `*IntegrationTest.java`

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/*IntegrationTest.java</exclude>
        </excludes>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*IntegrationTest.java</include>
        </includes>
    </configuration>
</plugin>
```

### JaCoCo Coverage Rules
* **Minimum 80% line coverage** enforced at build time.
* **Exclude from coverage:** `entity/`, `dto/`, `mapper/`, `config/` packages (generated/boilerplate code).
* **100% coverage expected** on domain logic: pricing engine, availability checks, plan enforcement, GDPR erasure.

### CI Pipeline
* `mvn test` -- runs unit tests only (fast, no Docker required)
* `mvn verify` -- runs unit + integration tests (requires Docker for Testcontainers)
* CI runs `mvn clean verify` on every push to ensure both pass.
