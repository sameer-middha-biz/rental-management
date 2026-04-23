# Phase 1-2-3 Code Review Findings

**Date:** 2026-04-02
**Scope:** Phase 1 (Project Setup & Shared Kernel), Phase 2 (Security & Auth Foundation), Phase 3 (Tenant & User Modules)
**Reviewer:** Claude Code
**Status:** Open

---

## Summary

| Severity | Count | IDs |
|----------|-------|-----|
| CRITICAL | 3 | #1, #2, #3 |
| HIGH | 3 | #4, #5, #6 |
| MEDIUM | 6 | #7, #8, #9, #10, #11, #12 |
| LOW | 4 | #13, #14, #15, #16 |
| **Total** | **16** | |

---

## CRITICAL

### Issue #1 — Global Email Uniqueness Constraint Breaks Multi-Tenancy

- **Category:** Security / Architecture
- **File:** `src/main/resources/db/migration/V2.1__create_users_table.sql` (lines 22-25)
- **Phase:** 3

**Current code:**
```sql
-- Line 22: Per-tenant uniqueness (correct)
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email);

-- Line 25: Global uniqueness (PROBLEM)
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
```

**Problem:** Both constraints are active simultaneously. The global constraint means an email registered in Tenant A blocks the same email from being used in Tenant B. This couples tenants at the database level and violates the core multi-tenancy isolation principle.

**Example:**
```
1. Agency "SunStay Villas" (tenant AAA) registers admin alice@gmail.com   -> OK
2. Agency "CoastalHomes"   (tenant BBB) registers admin alice@gmail.com   -> CONSTRAINT VIOLATION
   PostgreSQL: duplicate key value violates unique constraint "uq_users_email"
```
Alice is a freelance property manager working for both agencies. She can never join CoastalHomes.

**Impact:** Tenants are coupled. One tenant's user data blocks another tenant's onboarding.

**Fix:** Create a new Flyway migration to drop the global constraint:
```sql
ALTER TABLE users DROP CONSTRAINT uq_users_email;
```
Then update the login flow to resolve tenant from email + password combination (query all matching users, verify password against each), or require tenant identifier at login.

---

### Issue #2 — Cross-Tenant Email Enumeration via `existsByEmail()`

- **Category:** Security
- **Files:**
  - `src/main/java/com/rental/pms/modules/user/service/InvitationService.java` (line 71)
  - `src/main/java/com/rental/pms/modules/user/service/InvitationService.java` (line 120)
  - `src/main/java/com/rental/pms/modules/tenant/service/TenantRegistrationService.java` (line 56)
- **Phase:** 3

**Current code (InvitationService.java:71):**
```java
if (userRepository.existsByEmail(request.email())) {
    throw new ConflictException("Email already registered", "USER.EMAIL.DUPLICATE");
}
```

**Problem:** `existsByEmail()` checks across ALL tenants (no `tenant_id` filter) and returns a distinct error message. An attacker who is an AGENCY_ADMIN of any tenant can enumerate emails of users in other tenants by attempting invitations.

**Example attack:**
```
Attacker is AGENCY_ADMIN of tenant "EvilCorp". They call:

POST /api/v1/invitations { "email": "ceo@competitor.com", "roleName": "HOUSEKEEPER" }
-> 409 "Email already registered"     <- CONFIRMED: email exists on the platform

POST /api/v1/invitations { "email": "nobody@random.com", "roleName": "HOUSEKEEPER" }
-> 201 Created                        <- Not registered

The attacker now knows ceo@competitor.com uses this platform and can target them.
```

**Impact:** Information disclosure of users across all tenants. Enables targeted phishing.

**Fix:**
1. After fixing Issue #1 (removing global uniqueness), change `existsByEmail()` to `existsByEmailAndTenantId()` so the check is tenant-scoped.
2. In `TenantRegistrationService.register()`, return a generic response instead of confirming email existence.

---

### Issue #3 — Refresh Token Rotation Race Condition (TOCTOU)

- **Category:** Security
- **File:** `src/main/java/com/rental/pms/modules/user/service/AuthService.java` (lines 106-119)
- **Phase:** 2

**Current code:**
```java
// Line 106: READ the token
RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new TokenInvalidException("Refresh"));

// Line 109: CHECK if revoked
if (storedToken.isRevoked()) {
    throw new TokenInvalidException("Refresh");
}

// Line 118: WRITE revocation
storedToken.setRevoked(true);
refreshTokenRepository.save(storedToken);
```

**Problem:** Time-Of-Check-Time-Of-Use (TOCTOU) race condition. Two concurrent requests with the same refresh token can both pass the `isRevoked()` check before either writes the revocation, resulting in two new valid token pairs from a single refresh token.

**Example:**
```
Thread A at T=0ms: findByTokenHash(hash)  -> storedToken (revoked=false)
Thread B at T=0ms: findByTokenHash(hash)  -> storedToken (revoked=false)
Thread A at T=1ms: isRevoked() -> false   (passes check)
Thread B at T=1ms: isRevoked() -> false   (passes check)
Thread A at T=2ms: setRevoked(true), save()
Thread B at T=3ms: setRevoked(true), save()

Result: Both threads issue new access + refresh tokens.
Attacker now has 2 valid sessions from 1 stolen refresh token.
```

**Impact:** Token reuse attack. Attacker can maintain persistent access even after the legitimate user refreshes.

**Fix:** Replace the read-check-write pattern with an atomic operation:
```java
// Option A: Atomic UPDATE with row count check
@Modifying
@Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.tokenHash = :hash AND rt.revoked = false")
int revokeByTokenHashIfNotRevoked(@Param("hash") String hash);

// In service: if (revokeByTokenHashIfNotRevoked(hash) == 0) throw TokenInvalidException
```
Or use `@Version` optimistic locking on `RefreshToken` entity so the second concurrent save throws `OptimisticLockException`.

---

## HIGH

### Issue #4 — Rate Limit Bypass via X-Forwarded-For Header Spoofing

- **Category:** Security
- **File:** `src/main/java/com/rental/pms/common/security/RateLimitFilter.java` (lines 113-119)
- **Phase:** 1

**Current code:**
```java
private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
        return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}
```

**Problem:** `X-Forwarded-For` is a client-controlled header. An attacker can spoof different IPs to get a fresh rate limit bucket for every request, completely bypassing the anonymous 20-requests-per-minute limit.

**Example attack:**
```bash
# First 20 requests hit the limit normally
curl -X POST /api/v1/auth/login -d '...'   # 20x -> 429 Too Many Requests

# Attacker spoofs headers to bypass:
curl -H "X-Forwarded-For: 10.0.0.1" -X POST /api/v1/auth/login -d '...'  -> 401 (new bucket)
curl -H "X-Forwarded-For: 10.0.0.2" -X POST /api/v1/auth/login -d '...'  -> 401 (new bucket)
curl -H "X-Forwarded-For: 10.0.0.3" -X POST /api/v1/auth/login -d '...'  -> 401 (new bucket)
# Unlimited login attempts!
```

**Impact:** Rate limiting is completely ineffective for brute-force protection on public endpoints.

**Fix:** Either:
1. Only trust `X-Forwarded-For` when a known proxy IP is configured (allowlist trusted proxies).
2. Use `request.getRemoteAddr()` exclusively when not behind a reverse proxy.
3. Use Spring's `ForwardedHeaderFilter` with proper trusted proxy configuration.

---

### Issue #5 — N+1 Query in `InvitationService.getInvitations()`

- **Category:** Performance
- **File:** `src/main/java/com/rental/pms/modules/user/service/InvitationService.java` (lines 170-178)
- **Phase:** 3

**Current code:**
```java
Page<InvitationResponse> page = invitationRepository.findAllByTenantId(tenantId, pageable)
        .map(inv -> {
            String roleName = roleRepository.findById(inv.getRoleId())  // 1 query PER invitation
                    .map(Role::getName)
                    .orElse("UNKNOWN");
            return toResponse(inv, roleName);
        });
```

**Problem:** Each invitation in the page triggers a separate `roleRepository.findById()` query. With page size 20, this is 21 database queries (1 for the page + 20 for roles). A malicious client sending `?size=1000` would trigger 1001 queries.

**Example — `GET /api/v1/invitations?page=0&size=20`:**
```sql
SELECT * FROM invitations WHERE tenant_id = 'AAA' LIMIT 20;     -- Query 1
SELECT * FROM roles WHERE id = 'role-uuid-1';                    -- Query 2
SELECT * FROM roles WHERE id = 'role-uuid-1';                    -- Query 3 (same role!)
SELECT * FROM roles WHERE id = 'role-uuid-2';                    -- Query 4
-- ... 17 more role queries ...
-- Total: 21 queries for one page request
```

**Impact:** Database overload under normal usage. DoS vector with large page sizes.

**Fix:** Either:
1. Write a custom JPQL query with `JOIN FETCH` to load roles with invitations in a single query.
2. Pre-load all roles into a `Map<UUID, String>` at the start of the method (only ~6 system roles exist).
3. Add a `@Cacheable` annotation on `roleRepository.findById()` since roles are static data.

---

### Issue #6 — `UpdateUserStatusRequest` Causes 500 on Invalid Input

- **Category:** Functional
- **Files:**
  - `src/main/java/com/rental/pms/modules/user/dto/UpdateUserStatusRequest.java` (lines 5-7)
  - `src/main/java/com/rental/pms/modules/user/service/UserService.java` (line 92)
- **Phase:** 3
- **Also flagged by:** CodeRabbit finding #5

**Current code:**
```java
// DTO: status is just a raw String with no enum validation
public record UpdateUserStatusRequest(
        @NotBlank String status
) {}

// Service: blind valueOf() call
UserStatus newStatus = UserStatus.valueOf(request.status());
```

**Problem:** If a client sends `{ "status": "BANNED" }`, `UserStatus.valueOf("BANNED")` throws `IllegalArgumentException`. This is NOT a `JwtException` or business exception, so `GlobalExceptionHandler` catches it as a generic unhandled exception and returns HTTP 500.

**Example:**
```
PATCH /api/v1/users/{id}/status  { "status": "BANNED" }

UserStatus.valueOf("BANNED")
-> IllegalArgumentException: No enum constant com.rental.pms.modules.user.entity.UserStatus.BANNED
-> GlobalExceptionHandler: HTTP 500 Internal Server Error (should be 400)
```

**Impact:** Bad client input triggers a 500 instead of a clean 400 validation error. Same issue exists for `TenantStatus.valueOf()` in `TenantService.java:69` (CodeRabbit finding #4).

**Fix:** Add `@Pattern` validation on the DTO:
```java
public record UpdateUserStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED") String status
) {}
```
Or wrap `valueOf()` in a try-catch that throws a business exception with a 400 status.

---

## MEDIUM

### Issue #7 — Unbounded In-Memory Rate Limit Fallback Map

- **Category:** Performance
- **File:** `src/main/java/com/rental/pms/common/security/RateLimitFilter.java` (lines 44-48, 96-104)
- **Phase:** 1

**Current code:**
```java
private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();
// No maximum size, no TTL, no eviction policy

return localBuckets.computeIfAbsent(key, k -> Bucket.builder()
        .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
        .build());
```

**Problem:** When Redis is unavailable, the fallback map accumulates a new `Bucket` object for every unique IP/tenant key. No entries are ever removed. During a Redis outage with high traffic, this can exhaust JVM heap memory.

**Example:**
```
1. Redis goes down at 2:00 PM
2. Over the next hour, 50,000 unique IPs hit public endpoints
3. localBuckets map grows to 50,000 entries (~10 MB)
4. Redis stays down for 4 hours during a traffic spike
5. Map reaches 200,000+ entries -> GC pressure -> OutOfMemoryError
```

**Impact:** Memory exhaustion during Redis outages.

**Fix:** Use `Caffeine` cache with a TTL and maximum size:
```java
private final Cache<String, Bucket> localBuckets = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofMinutes(5))
        .build();
```

---

### Issue #8 — Unhandled `UUID.fromString()` in JwtTokenProvider

- **Category:** Functional
- **File:** `src/main/java/com/rental/pms/common/security/JwtTokenProvider.java` (lines 102-104, 109-112)
- **Phase:** 2

**Current code:**
```java
public UUID getUserId(Claims claims) {
    return UUID.fromString(claims.getSubject());  // No try-catch
}

public UUID getTenantId(Claims claims) {
    String tenantId = claims.get("tenantId", String.class);
    return tenantId != null ? UUID.fromString(tenantId) : null;  // No try-catch
}
```

**Problem:** If the JWT subject or tenantId claim contains an invalid UUID string, `UUID.fromString()` throws `IllegalArgumentException`. This is NOT a `JwtException`, so the `catch (JwtException)` block in `JwtAuthenticationFilter` does not catch it. The exception propagates as an unhandled 500.

**Example:**
```
A JWT is correctly RS256-signed but was issued by a buggy app version
that set subject to "user-email@test.com" instead of a UUID.

JwtAuthenticationFilter calls: jwtTokenProvider.getUserId(claims)
-> UUID.fromString("user-email@test.com")
-> IllegalArgumentException (NOT a JwtException)
-> Falls through catch blocks -> HTTP 500 (should be 401)
```

**Impact:** Malformed but validly-signed tokens cause 500 instead of 401.

**Fix:** Wrap in try-catch:
```java
public UUID getUserId(Claims claims) {
    try {
        return UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException e) {
        throw new JwtException("Invalid userId in token", e);
    }
}
```

---

### Issue #9 — Missing `ON DELETE CASCADE` on `invitations.invited_by`

- **Category:** Functional / Data Integrity
- **File:** `src/main/resources/db/migration/V2.4__create_invitations_table.sql` (line 8)
- **Phase:** 3

**Current code:**
```sql
invited_by  UUID  NOT NULL REFERENCES users(id),
```

**Problem:** The `invited_by` foreign key has no `ON DELETE` clause. If an admin deletes a user who previously created invitations, PostgreSQL blocks the deletion with a foreign key violation error, causing an unhandled 500.

**Example:**
```
1. Alice (user_id=AAA) invites bob@test.com and charlie@test.com
   invitations table: { id: I1, invited_by: AAA }, { id: I2, invited_by: AAA }

2. Admin deletes Alice: DELETE /api/v1/users/AAA
   -> UserService.deleteUser() calls userRepository.delete(user)
   -> PostgreSQL: DELETE FROM users WHERE id = 'AAA'
   -> ERROR: update or delete on table "users" violates foreign key constraint
             "invitations_invited_by_fkey" on table "invitations"
   -> HTTP 500 Internal Server Error
```

**Impact:** Cannot delete any user who has ever created an invitation.

**Fix:** Create a new Flyway migration:
```sql
ALTER TABLE invitations DROP CONSTRAINT invitations_invited_by_fkey;
ALTER TABLE invitations ALTER COLUMN invited_by DROP NOT NULL;
ALTER TABLE invitations ADD CONSTRAINT invitations_invited_by_fkey
    FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL;
```

---

### Issue #10 — Encryption Key Accepts Raw UTF-8 Strings

- **Category:** Security
- **File:** `src/main/java/com/rental/pms/common/encryption/EncryptedStringConverter.java` (lines 36-43)
- **Phase:** 1

**Current code:**
```java
public EncryptedStringConverter(@Value("${pms.encryption.key}") String encryptionKey) {
    byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length != 32) {
        throw new IllegalArgumentException(
                "Encryption key must be exactly 32 bytes for AES-256. Received: " + keyBytes.length);
    }
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
}
```

**Problem:** The key is taken as a raw UTF-8 string and validated by byte count. This has two issues:
1. A 32-character ASCII string (e.g., `abcdefghijklmnopqrstuvwxyz123456`) has lower entropy than a true 256-bit random key. Printable ASCII uses ~6.5 bits per character, giving ~208 bits instead of 256.
2. Multibyte UTF-8 characters make the byte count unpredictable. A 32-char string with accented characters (`a...zneuo`) produces >32 bytes and fails validation with a confusing error.

**Example:**
```yaml
# Works but has reduced entropy (~208 bits instead of 256):
PMS_ENCRYPTION_KEY=abcdefghijklmnopqrstuvwxyz123456

# Fails with confusing error (38 bytes, not 32):
PMS_ENCRYPTION_KEY=abcdefghijklmnopqrstuvwxyzneuo

# Proper 256-bit key (Base64-encoded):
PMS_ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

**Impact:** Weaker-than-expected encryption. Confusing configuration errors with non-ASCII keys.

**Fix:** Accept Base64-encoded keys:
```java
byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
if (keyBytes.length != 32) {
    throw new IllegalArgumentException("Encryption key must decode to exactly 32 bytes for AES-256");
}
```

---

### Issue #11 — Missing Explicit Security Headers

- **Category:** Security
- **File:** `src/main/java/com/rental/pms/config/SecurityConfig.java` (lines 58-91)
- **Phase:** 2

**Current code:**
```java
http
    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(...)
    // No explicit headers() configuration
```

**Problem:** The security configuration does not explicitly set critical HTTP security headers. While Spring Security provides some defaults, they should be explicitly configured for defense-in-depth.

**Missing headers:**
- `X-Frame-Options: DENY` (clickjacking protection)
- `X-Content-Type-Options: nosniff` (MIME-sniffing prevention)
- `Strict-Transport-Security: max-age=31536000` (HSTS)
- `Content-Security-Policy` (XSS protection for direct booking website)
- `Cache-Control: no-store` (prevent caching of sensitive API responses)

**Example clickjacking attack (without X-Frame-Options):**
```html
<!-- Attacker's site: evil.com/steal.html -->
<iframe src="https://yourapp.com/dashboard" style="opacity:0; position:absolute;"></iframe>
<button style="position:absolute; top:200px;">Click here to win a prize!</button>
<!-- User clicks the invisible iframe, performing actions on the real app -->
```

**Impact:** Increased vulnerability to clickjacking, MIME-sniffing, and cache-based attacks.

**Fix:** Add explicit header configuration:
```java
http.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
    .cacheControl(Customizer.withDefaults())
);
```

---

### Issue #12 — CORS Origin Split Doesn't Trim Whitespace

- **Category:** Functional
- **File:** `src/main/java/com/rental/pms/config/SecurityConfig.java` (line 101)
- **Phase:** 2

**Current code:**
```java
configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
```

**Problem:** If the configuration value contains spaces after commas (a very common formatting habit), the second origin has a leading space and will never match any browser `Origin` header.

**Example:**
```yaml
# application.yml (common formatting with space after comma):
pms:
  cors:
    allowed-origins: "http://localhost:3000, https://app.rentalplatform.com"
```
```java
allowedOrigins.split(",")
-> ["http://localhost:3000", " https://app.rentalplatform.com"]
//                           ^ leading space

// Browser sends: Origin: https://app.rentalplatform.com
// Spring compares against: " https://app.rentalplatform.com"
// No match -> CORS blocks the production frontend!
```

**Impact:** Production CORS failures from an extremely hard-to-debug configuration typo.

**Fix:**
```java
configuration.setAllowedOrigins(
    Arrays.stream(allowedOrigins.split(","))
          .map(String::trim)
          .toList()
);
```

---

## LOW

### Issue #13 — `UserStatus.INVITED` Enum Value Is Dead Code

- **Category:** Functional / Code Quality
- **File:** `src/main/java/com/rental/pms/modules/user/entity/UserStatus.java`
- **Phase:** 3

**Current code:**
```java
public enum UserStatus {
    ACTIVE,
    INVITED,   // Never set anywhere in the codebase
    DISABLED
}
```

**Problem:** The `INVITED` status is defined but never used. Both `InvitationService.acceptInvite()` and `TenantRegistrationService.register()` set new users to `ACTIVE` directly. The concept of "invited user" is tracked via the `invitations` table instead.

**Example of confusion:**
```java
// A developer writes a query expecting to find pending users:
userRepository.findByStatus(UserStatus.INVITED)
-> Returns ZERO results, always, because no code path ever sets this status.
```

**Impact:** Misleading code. Could cause bugs if a developer relies on it.

**Fix:** Either remove `INVITED` from the enum (if the invitation table is the source of truth), or use it in `acceptInvite()` as part of a two-step activation flow.

---

### Issue #14 — AsyncConfig Missing Rejection Policy

- **Category:** Performance
- **File:** `src/main/java/com/rental/pms/config/AsyncConfig.java` (lines 35-44)
- **Phase:** 1

**Current code:**
```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(corePoolSize);
executor.setMaxPoolSize(maxPoolSize);
executor.setQueueCapacity(queueCapacity);
// No setRejectedExecutionHandler() -- default is AbortPolicy
```

**Problem:** When all threads are busy and the queue is full, the default `AbortPolicy` throws `RejectedExecutionException`. If an `@Async` audit or email task is rejected, the exception propagates to the calling request thread, crashing the business operation.

**Example:**
```
1. 10 async threads busy sending audit events
2. Queue capacity of 100 is full
3. New booking triggers @Async auditEventPublisher.publish(...)
4. ThreadPoolExecutor rejects the task -> RejectedExecutionException
5. Exception propagates to BookingService caller
6. HTTP 500 on booking creation -- because the AUDIT LOG queue was full
```

**Impact:** Non-critical subsystem (audit) can crash critical operations (bookings).

**Fix:**
```java
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```
`CallerRunsPolicy` runs the rejected task on the calling thread instead of throwing. The request is slightly slower but doesn't fail.

---

### Issue #15 — Hardcoded JSON Error Responses in Filters

- **Category:** Code Quality
- **Files:**
  - `src/main/java/com/rental/pms/common/security/JwtAuthenticationFilter.java` (lines 84-85, 91-92)
  - `src/main/java/com/rental/pms/common/security/RateLimitFilter.java` (lines 89-91)
- **Phase:** 2

**Current code:**
```java
response.getWriter().write(
    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token has expired\",\"errorCode\":\"AUTH.TOKEN.EXPIRED\"}");
```

**Problem:** Error responses are manually constructed JSON strings. If any future change introduces dynamic content (e.g., a request path or user identifier), special characters won't be properly escaped, producing invalid JSON.

**Example of future breakage:**
```java
// If someone later adds dynamic content:
"\"message\":\"Token expired for path: " + request.getRequestURI() + "\""

// Request to: /api/v1/search?q="test"
// Produces broken JSON: {"message":"Token expired for path: /api/v1/search?q="test""}
```

**Impact:** Not a bug today (messages are static), but fragile for future changes.

**Fix:** Use `ObjectMapper` to serialize an `ErrorResponse` record:
```java
objectMapper.writeValue(response.getWriter(), new ErrorResponse(401, "Unauthorized", "Token has expired", "AUTH.TOKEN.EXPIRED"));
```

---

### Issue #16 — No `jti` (JWT ID) Claim in Tokens

- **Category:** Security / Design
- **File:** `src/main/java/com/rental/pms/common/security/JwtTokenProvider.java` (lines 58-68)
- **Phase:** 2

**Current code:**
```java
return Jwts.builder()
        .subject(userId.toString())
        .claim("tenantId", ...)
        .claim("roles", ...)
        .claim("permissions", ...)
        // No .id(UUID.randomUUID().toString())  -- missing jti claim
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTokenExpiry)))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
```

**Problem:** JWT tokens have no unique identifier (`jti` claim). Without it, individual token revocation requires revoking ALL tokens for a user (`revokeAllByUserId`), which logs them out of every device.

**Example:**
```
User: "Someone stole my laptop. Can you revoke just that session?"
System: "We can only revoke ALL your sessions (phone, tablet, desktop)."

With jti, you could:
  1. Add jti to a Redis blacklist: SADD "token-blacklist" "jti-abc123"
  2. JwtAuthenticationFilter checks: SISMEMBER "token-blacklist" jti -> reject
  3. Only the stolen laptop session is revoked.
```

**Impact:** Coarse-grained token revocation only. Cannot revoke individual sessions.

**Fix:** Add `jti` claim to token generation:
```java
.id(UUID.randomUUID().toString())
```

---

## Cross-Reference with CodeRabbit Findings

Issues #6 (UserStatus.valueOf) overlaps with CodeRabbit finding #5.
Issues #6 also covers TenantStatus.valueOf which is CodeRabbit finding #4.
All other issues in this document are NEW findings not covered by CodeRabbit.

See: `docs/tasks/coderabbit-review-findings.md` for CodeRabbit-specific tracking.

---

## Fix Status

| # | Severity | Category | File(s) | Finding | Status |
|---|----------|----------|---------|---------|--------|
| 1 | CRITICAL | Security | `V2.1__create_users_table.sql` | Global email uniqueness breaks multi-tenancy | [x] |
| 2 | CRITICAL | Security | `InvitationService`, `TenantRegistrationService` | Cross-tenant email enumeration | [x] |
| 3 | CRITICAL | Security | `AuthService.java` | Refresh token rotation race condition | [x] |
| 4 | HIGH | Security | `RateLimitFilter.java` | X-Forwarded-For spoofing bypasses rate limits | [x] |
| 5 | HIGH | Performance | `InvitationService.java` | N+1 query on invitation listing | [x] |
| 6 | HIGH | Functional | `UpdateUserStatusRequest`, `UserService` | Invalid status enum causes 500 not 400 | [x] |
| 7 | MEDIUM | Performance | `RateLimitFilter.java` | Unbounded in-memory fallback map | [x] |
| 8 | MEDIUM | Functional | `JwtTokenProvider.java` | Unhandled UUID.fromString() exception | [x] |
| 9 | MEDIUM | Functional | `V2.4__create_invitations_table.sql` | Missing ON DELETE on invited_by FK | [x] |
| 10 | MEDIUM | Security | `EncryptedStringConverter.java` | UTF-8 key entropy issues | [x] |
| 11 | MEDIUM | Security | `SecurityConfig.java` | Missing explicit security headers | [x] |
| 12 | MEDIUM | Functional | `SecurityConfig.java` | CORS origins not trimmed | [x] |
| 13 | LOW | Quality | `UserStatus.java` | Dead `INVITED` enum value | [x] |
| 14 | LOW | Performance | `AsyncConfig.java` | Missing rejection policy | [x] |
| 15 | LOW | Quality | `JwtAuthenticationFilter`, `RateLimitFilter` | Hardcoded JSON error responses | [x] |
| 16 | LOW | Security | `JwtTokenProvider.java` | No jti claim for per-token revocation | [x] |
