# Phase 2: Security and Authentication Foundation

## Prerequisites
- Phase 1 complete (common/ package, BaseEntity, TenantInterceptor, event bus, GlobalExceptionHandler)

## Scope
- **In scope:** JWT provider, servlet filters (tenant, auth, rate limit), field encryption, Spring Security config
- **Out of scope:** User/Tenant entities (Phase 3), login/register endpoints (Phase 3), OAuth2 Google flow (Phase 3)

## Deliverables

### Database Migrations
None (no new tables in this phase).

### Java Classes

**Security Context (`common/security/`)**
| Class | Responsibility |
|-------|---------------|
| `TenantContext.java` | `ThreadLocal<UUID>` holder for current tenant ID. Static methods: `setTenantId()`, `getTenantId()`, `clear()` |
| `CurrentUser.java` | Helper to extract current user details from `SecurityContextHolder` |
| `JwtTokenProvider.java` | RS256 token creation and validation using JJWT. Claims: `sub` (userId), `tenantId`, `roles`, `permissions`. Access token: 15min. Refresh token: 7 days. |

**Servlet Filters**
| Class | Responsibility |
|-------|---------------|
| `JwtAuthenticationFilter.java` | `OncePerRequestFilter`: extracts Bearer token, validates via JwtTokenProvider, sets `UsernamePasswordAuthenticationToken` in SecurityContext |
| `TenantFilter.java` | Runs after JwtAuthenticationFilter: extracts `tenantId` from JWT claims, sets `TenantContext`, populates MDC (`tenantId`, `userId`, `requestId`). Skips for SUPER_ADMIN. Activates Hibernate tenant filter. |
| `RateLimitFilter.java` | Bucket4j + Redis rate limiting. Per-tenant limits for authenticated requests, per-IP for anonymous. Returns 429 with `Retry-After` header. |

**Field Encryption (`common/encryption/`)**
| Class | Responsibility |
|-------|---------------|
| `EncryptedStringConverter.java` | JPA `@Converter` using AES-256-GCM. Encrypts on write, decrypts on read. Key from env var `ENCRYPTION_KEY`. |

**Spring Security Config (`config/SecurityConfig.java`)**
- `@EnableMethodSecurity` for `@PreAuthorize` support
- Filter chain: JwtAuthenticationFilter -> TenantFilter -> RateLimitFilter
- Public endpoints: `/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`
- All other endpoints require authentication
- CORS configuration for frontend origin
- CSRF disabled (stateless JWT)
- Session management: STATELESS

**RSA Key Setup**

Generate keys for local development:
```bash
mkdir -p src/main/resources/keys
openssl genrsa -out src/main/resources/keys/private.pem 2048
openssl rsa -in src/main/resources/keys/private.pem -pubout -out src/main/resources/keys/public.pem
```

### Domain Events
- None published in this phase

### Services
- `JwtTokenProvider` (generates and validates tokens, but no login flow yet)

## Acceptance Criteria
1. `JwtTokenProvider` generates a valid RS256-signed JWT with `sub`, `tenantId`, `roles`, `permissions` claims
2. `JwtTokenProvider` rejects expired tokens, tampered tokens, and tokens signed with wrong key
3. `JwtAuthenticationFilter` sets `Authentication` in `SecurityContextHolder` for valid tokens
4. `JwtAuthenticationFilter` returns 401 for missing/invalid tokens on protected endpoints
5. `TenantFilter` sets `TenantContext` from JWT claims and populates MDC (`tenantId`, `userId`, `requestId`)
6. `TenantFilter` skips Hibernate filter activation for `SUPER_ADMIN` role
7. `TenantFilter` clears `TenantContext` and MDC in `finally` block
8. `RateLimitFilter` returns 429 when rate limit exceeded, with `Retry-After` header
9. `EncryptedStringConverter` encrypts and decrypts strings correctly with AES-256-GCM
10. `SecurityConfig` allows unauthenticated access to `/api/v1/auth/**` and `/actuator/health`
11. `SecurityConfig` blocks unauthenticated access to all other endpoints (401)

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `JwtTokenProviderTest` | Unit | Generate token, validate valid token, reject expired, reject tampered, extract claims |
| `JwtAuthenticationFilterTest` | Unit | Valid token sets auth, missing token skips, invalid token returns 401 |
| `TenantFilterTest` | Unit | Sets TenantContext, populates MDC, clears on completion, skips for SUPER_ADMIN |
| `RateLimitFilterTest` | Unit | Allows requests within limit, returns 429 when exceeded |
| `EncryptedStringConverterTest` | Unit | Encrypt -> decrypt roundtrip, different ciphertexts for same plaintext (random IV) |
| `SecurityConfigIntegrationTest` | Integration | Public endpoints accessible, protected endpoints return 401 |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Application starts with security filters active
- Protected endpoints return 401 without token
- No compiler warnings
- Code follows `docs/CODING_STANDARDS.md`
