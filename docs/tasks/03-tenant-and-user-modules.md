# Phase 3: Tenant and User Modules

## Prerequisites
- Phase 1 complete (common/ package, BaseEntity, event bus)
- Phase 2 complete (JwtTokenProvider, TenantFilter, SecurityConfig)

## Scope
- **Modules:** Module 0 (Tenant Management), Module 1 (User Management & Roles)
- **In scope:** Tenant/User entities, registration flow, auth flow (login/refresh/logout), RBAC, invitations
- **Out of scope:** OAuth2 Google login (can be deferred), 2FA TOTP (can be added after core auth)

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V2.0__create_tenants_table.sql` | `tenants` | NOT tenant-scoped (no tenant_id FK). Columns per ARCHITECTURE.md 4.5 |
| `V2.1__create_users_table.sql` | `users` | Tenant-scoped. UNIQUE(tenant_id, email) |
| `V2.2__create_roles_permissions_tables.sql` | `roles`, `permissions`, `role_permissions`, `user_roles` | Join tables for RBAC |
| `V2.3__create_refresh_tokens_table.sql` | `refresh_tokens` | Token hash, userId, expiresAt, revoked flag |
| `V2.4__create_invitations_table.sql` | `invitations` | Tenant-scoped. Token, email, role, expiry, status |
| `V2.5__seed_roles_and_permissions.sql` | *(seed data)* | Insert 6 system roles and all permissions |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `Tenant` | **No** | id, name, slug, customDomain, timezone, defaultCurrency, managementFeeType/Percentage/Fixed, logoS3Key, contactEmail, status |
| `User` | Yes | id, tenantId, email, passwordHash, firstName, lastName, phone, status, twoFactorEnabled, twoFactorSecret, lastLoginAt |
| `Role` | No (system) | id, name, description, isSystem |
| `Permission` | No (system) | id, code, description, module |
| `RefreshToken` | Yes | id, userId, tokenHash, expiresAt, revoked |
| `Invitation` | Yes | id, tenantId, email, roleId, token, expiresAt, status |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `POST` | `/api/v1/auth/register` | `TenantRegistrationRequest` | `AuthResponse` (tokens) | Public |
| `POST` | `/api/v1/auth/login` | `LoginRequest` (email, password) | `AuthResponse` | Public |
| `POST` | `/api/v1/auth/refresh` | `RefreshTokenRequest` | `AuthResponse` | Public |
| `POST` | `/api/v1/auth/logout` | `RefreshTokenRequest` | 204 | Authenticated |
| `GET` | `/api/v1/tenant` | -- | `TenantResponse` | `TENANT_VIEW` |
| `PUT` | `/api/v1/tenant` | `UpdateTenantRequest` | `TenantResponse` | `TENANT_MANAGE` |
| `GET` | `/api/v1/users` | Pageable | `PageResponse<UserResponse>` | `USER_VIEW` |
| `GET` | `/api/v1/users/{id}` | -- | `UserResponse` | `USER_VIEW` |
| `PUT` | `/api/v1/users/{id}` | `UpdateUserRequest` | `UserResponse` | `USER_MANAGE` |
| `POST` | `/api/v1/users/invite` | `InviteUserRequest` | `InvitationResponse` | `USER_MANAGE` |
| `POST` | `/api/v1/users/accept-invite` | `AcceptInviteRequest` | `AuthResponse` | Public |
| `GET` | `/api/v1/users/me` | -- | `UserResponse` | Authenticated |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `TenantRegisteredEvent` | `TenantRegistrationService` | (future: onboarding, notifications) |
| `UserCreatedEvent` | `AuthService` / `InvitationService` | (future: audit, notifications) |

### Services
| Service | Key Methods |
|---------|------------|
| `TenantService` | `getTenant()`, `updateTenant()`, `getLogoUploadUrl()` |
| `TenantRegistrationService` | `register(dto)` -- atomic: create Tenant + Admin User + Starter Subscription |
| `AuthService` | `login()`, `refreshToken()`, `logout()` |
| `UserService` | `getUsers()`, `getUser()`, `updateUser()`, `getCurrentUser()` |
| `InvitationService` | `invite()`, `acceptInvite()` |

### Registration Flow (Sequence)
```
POST /api/v1/auth/register { email, password, firstName, lastName, agencyName }
  -> BEGIN TRANSACTION
  -> Create Tenant { name: agencyName, slug: generated, currency: GBP, timezone: UTC }
  -> Create User { email, passwordHash, tenantId, status: ACTIVE }
  -> Assign role: AGENCY_ADMIN
  -> SubscriptionService.createStarterSubscription(tenantId)   // Phase 4 stub
  -> COMMIT TRANSACTION
  -> Generate JWT (accessToken + refreshToken)
  -> Publish TenantRegisteredEvent
  -> Return AuthResponse
```

## Acceptance Criteria
1. Registration creates Tenant, Admin User, and Starter Subscription atomically (all-or-nothing)
2. Login with correct credentials returns accessToken (15min) + refreshToken (7 days)
3. Login with wrong credentials returns 401 with error code `AUTH.CREDENTIALS.INVALID`
4. Refresh token rotates: old token invalidated, new pair issued
5. Logout revokes refresh token
6. `User` entity is tenant-scoped (Hibernate filter applied)
7. `Tenant` entity is NOT tenant-scoped (no tenant_id column)
8. Users can only see/manage users within their own tenant
9. Invitation flow: invite -> email with token -> accept creates user with assigned role
10. `GET /api/v1/users` returns paginated, tenant-scoped results
11. System roles (SUPER_ADMIN, AGENCY_ADMIN, etc.) are seeded and non-deletable

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `TenantRegistrationServiceTest` | Unit | Successful registration, duplicate email, atomic rollback on failure |
| `AuthServiceTest` | Unit | Login success, wrong password, expired refresh token, token rotation |
| `UserServiceTest` | Unit | Get users, update user, current user from SecurityContext |
| `InvitationServiceTest` | Unit | Create invitation, accept with valid token, reject expired token |
| `AuthControllerIntegrationTest` | Integration | Register -> login -> refresh -> logout full flow |
| `UserRepositoryIntegrationTest` | Integration | Tenant isolation: User from Tenant A invisible to Tenant B |
| `TenantControllerIntegrationTest` | Integration | GET/PUT tenant settings |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Full auth flow works end-to-end (register -> login -> use token -> refresh -> logout)
- Tenant isolation verified via integration test
- Swagger docs generated for all endpoints
- No compiler warnings
