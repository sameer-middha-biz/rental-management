# Phase 3: Tenant and User Modules

**Status:** COMPLETED

## Prerequisites
- Phase 1 complete (common/ package, BaseEntity, event bus)
- Phase 2 complete (JwtTokenProvider, TenantFilter, SecurityConfig)

## Scope
- **Modules:** Module 0 (Tenant Management), Module 1 (User Management & Roles)
- **In scope:** Tenant/User entities, registration flow, auth flow (login/refresh/logout), password reset, RBAC, invitations, super admin tenant endpoints
- **Out of scope:** OAuth2 Google login (deferred), 2FA TOTP (deferred)

---

## Design Decisions

### 1. Email Globally Unique (MVP Simplification)
The `users` table has both `UNIQUE(tenant_id, email)` (per ARCHITECTURE.md) and `UNIQUE(email)` (global). This allows login with just email+password — no tenant ID needed at login. One email can only exist in one tenant. If multi-tenant email is needed later, a tenant-selection step can be added to login.

### 2. Hard Delete for Users (Not Soft Delete)
`DELETE /api/v1/users/{id}` physically deletes the user row. Associated `refresh_tokens` and `user_roles` are cascade-deleted. Protections: cannot delete yourself, cannot delete the last AGENCY_ADMIN in a tenant.

### 3. Invite Notification — Log + Event
Since the notification module is Phase 8, `InvitationService` publishes an `InvitationCreatedEvent` (for future listeners) and logs the invite URL at INFO level. When the notification module is built, it will listen for this event and send the email.

### 4. Permissions Follow ARCHITECTURE.md
- `USER_EDIT` for profile updates (`PUT /api/v1/users/{id}`)
- `USER_MANAGE` for admin operations (status changes, role assignment, delete)
- `USER_INVITE` for invitation management
- Updated from task's original `USER_MANAGE` on profile update to match ARCHITECTURE.md.

### 5. Subscription Stub in Subscription Module
`SubscriptionService` interface created in `modules/subscription/service/` with `SubscriptionServiceStub` implementation that logs. Phase 4 replaces the stub with real implementation.

### 6. TenantContext During Registration & Invite Accept
Registration and invite-accept are public endpoints (no JWT → no TenantFilter). `TenantRegistrationService` and `InvitationService` manually set `TenantContext.setTenantId()` after creating/finding the tenant, and clear it in a `finally` block. This is required because `User` and `Invitation` extend `BaseEntity`, and `TenantInterceptor` reads `TenantContext` on `@PrePersist`.

### 7. Refresh Tokens Hashed with SHA-256
Refresh tokens are stored as SHA-256 hex digests (not bcrypt). SHA-256 allows fast DB lookups by hash, while bcrypt would require loading all tokens and comparing one by one.

### 8. Password Reset Included in Phase 3
Added `password_reset_tokens` table migration (V2.5) and `PasswordResetService` with request/confirm endpoints. Reset token logged at INFO level (email sent in Phase 8 via notification module).

### 9. Super Admin Endpoints Included
Added `AdminTenantController` with `GET /api/v1/admin/tenants`, `GET /api/v1/admin/tenants/{id}`, and `PATCH /api/v1/admin/tenants/{id}/status`. All require `hasRole('SUPER_ADMIN')`.

---

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V2.0__create_tenants_table.sql` | `tenants` | NOT tenant-scoped (no tenant_id FK). Indexes on slug, custom_domain, status |
| `V2.1__create_users_table.sql` | `users` | Tenant-scoped. UNIQUE(tenant_id, email) + UNIQUE(email) for MVP login |
| `V2.2__create_roles_permissions_tables.sql` | `roles`, `permissions`, `role_permissions`, `user_roles` | Join tables for RBAC |
| `V2.3__create_refresh_tokens_table.sql` | `refresh_tokens` | ON DELETE CASCADE from users |
| `V2.4__create_invitations_table.sql` | `invitations` | Tenant-scoped. Indexes on token, tenant_id |
| `V2.5__create_password_reset_tokens_table.sql` | `password_reset_tokens` | ON DELETE CASCADE from users |
| `V2.6__seed_roles_and_permissions.sql` | *(seed data)* | 6 system roles, 39 permissions, role-permission mappings |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `Tenant` | **No** (standalone) | id, name, slug, customDomain, timezone, defaultCurrency, managementFeeType/Percentage/Fixed, logoS3Key, contactEmail, status |
| `User` | Yes (extends BaseEntity) | email, passwordHash, firstName, lastName, phone, status, twoFactorEnabled, twoFactorSecret, lastLoginAt, roles (ManyToMany) |
| `Role` | No (system) | id, name, description, isSystem, permissions (ManyToMany) |
| `Permission` | No (system) | id, code, description, module |
| `RefreshToken` | No (standalone) | id, userId, tokenHash, expiresAt, revoked |
| `Invitation` | Yes (extends BaseEntity) | email, roleId, token, invitedBy, status, expiresAt |
| `PasswordResetToken` | No (standalone) | id, userId, tokenHash, expiresAt, used |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `POST` | `/api/v1/auth/register` | `TenantRegistrationRequest` | `AuthResponse` (tokens) | Public |
| `POST` | `/api/v1/auth/login` | `LoginRequest` (email, password) | `AuthResponse` | Public |
| `POST` | `/api/v1/auth/refresh` | `RefreshTokenRequest` | `AuthResponse` | Public |
| `POST` | `/api/v1/auth/logout` | `RefreshTokenRequest` | 204 | Authenticated |
| `POST` | `/api/v1/auth/password-reset/request` | `PasswordResetRequest` | 200 | Public |
| `POST` | `/api/v1/auth/password-reset/confirm` | `PasswordResetConfirmRequest` | 200 | Public |
| `GET` | `/api/v1/tenant` | -- | `TenantResponse` | `TENANT_VIEW` |
| `PUT` | `/api/v1/tenant` | `UpdateTenantRequest` | `TenantResponse` | `TENANT_MANAGE` |
| `GET` | `/api/v1/admin/tenants` | Pageable | `PageResponse<TenantResponse>` | `SUPER_ADMIN` |
| `GET` | `/api/v1/admin/tenants/{id}` | -- | `TenantResponse` | `SUPER_ADMIN` |
| `PATCH` | `/api/v1/admin/tenants/{id}/status` | `UpdateTenantStatusRequest` | `TenantResponse` | `SUPER_ADMIN` |
| `GET` | `/api/v1/users` | Pageable | `PageResponse<UserResponse>` | `USER_VIEW` |
| `GET` | `/api/v1/users/me` | -- | `UserResponse` | Authenticated |
| `GET` | `/api/v1/users/{id}` | -- | `UserResponse` | `USER_VIEW` |
| `PUT` | `/api/v1/users/{id}` | `UpdateUserRequest` | `UserResponse` | `USER_EDIT` or `USER_MANAGE` |
| `PATCH` | `/api/v1/users/{id}/status` | `UpdateUserStatusRequest` | `UserResponse` | `USER_MANAGE` |
| `PATCH` | `/api/v1/users/{id}/roles` | `UpdateUserRolesRequest` | `UserResponse` | `USER_MANAGE` |
| `DELETE` | `/api/v1/users/{id}` | -- | 204 | `USER_MANAGE` |
| `POST` | `/api/v1/invitations` | `InviteUserRequest` | `InvitationResponse` | `USER_INVITE` |
| `GET` | `/api/v1/invitations` | Pageable | `PageResponse<InvitationResponse>` | `USER_INVITE` |
| `POST` | `/api/v1/invitations/{token}/accept` | `AcceptInviteRequest` | `AuthResponse` | Public |
| `DELETE` | `/api/v1/invitations/{id}` | -- | 204 | `USER_INVITE` |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `TenantRegisteredEvent` | `TenantRegistrationService` | (future: onboarding, notifications) |
| `UserCreatedEvent` | `TenantRegistrationService` / `InvitationService` | (future: audit, notifications) |
| `InvitationCreatedEvent` | `InvitationService` | (future: notification module sends invite email) |
| `UserDeletedEvent` | `UserService` | (future: audit, cleanup) |

### Services
| Service | Key Methods |
|---------|------------|
| `TenantRegistrationService` | `register(dto)` — atomic: create Tenant + set TenantContext + create Admin User + assign AGENCY_ADMIN + create starter subscription + generate JWT + publish events |
| `TenantService` | `getTenant()`, `updateTenant()`, `getAllTenants()`, `getTenantById()`, `updateTenantStatus()` |
| `AuthService` | `login()`, `refreshToken()`, `logout()` |
| `PasswordResetService` | `requestPasswordReset()`, `confirmPasswordReset()` |
| `UserService` | `getUsers()`, `getUser()`, `getCurrentUser()`, `updateUser()`, `updateUserStatus()`, `updateUserRoles()`, `deleteUser()` |
| `InvitationService` | `invite()`, `acceptInvite()`, `getInvitations()`, `revokeInvitation()` |
| `SubscriptionServiceStub` | `createStarterSubscription()` — logs only, replaced in Phase 4 |

### Additional Components
| Component | Location | Purpose |
|-----------|----------|---------|
| `PmsPermissionEvaluator` | `common/security/` | `@Component("permissionEvaluator")` for `@PreAuthorize` SpEL expressions |
| 6 custom exceptions | `modules/user/exception/` | `InvalidCredentialsException`, `TokenExpiredException`, `TokenInvalidException`, `UserDisabledException`, `InvitationExpiredException`, `LastAdminException` |

### Registration Flow (Sequence)
```
POST /api/v1/auth/register { email, password, firstName, lastName, agencyName }
  -> BEGIN TRANSACTION
  -> Check email not already registered
  -> Generate unique slug from agencyName
  -> Create Tenant { name: agencyName, slug: generated, currency: GBP, timezone: UTC }
  -> TenantContext.setTenantId(tenant.id)    // Required for BaseEntity TenantInterceptor
  -> Create User { email, passwordHash, tenantId, status: ACTIVE }
  -> Assign role: AGENCY_ADMIN
  -> SubscriptionService.createStarterSubscription(tenantId)   // Phase 4 stub
  -> COMMIT TRANSACTION
  -> Generate JWT (accessToken + refreshToken)
  -> Save hashed refresh token to DB
  -> Publish TenantRegisteredEvent, UserCreatedEvent
  -> TenantContext.clear()
  -> Return AuthResponse
```

### Config Changes
- `SecurityConfig.java` — added `/api/v1/invitations/*/accept` to public endpoints
- `application.yml` — added `pms.invitation.expiry-days` and `pms.password-reset.expiry-hours`

---

## Acceptance Criteria
1. Registration creates Tenant, Admin User, and Starter Subscription atomically (all-or-nothing)
2. Login with correct credentials returns accessToken (15min) + refreshToken (7 days)
3. Login with wrong credentials returns 401 with error code `AUTH.CREDENTIALS.INVALID`
4. Refresh token rotates: old token invalidated, new pair issued
5. Logout revokes refresh token
6. `User` entity is tenant-scoped (Hibernate filter applied)
7. `Tenant` entity is NOT tenant-scoped (no tenant_id column)
8. Users can only see/manage users within their own tenant
9. Invitation flow: invite -> log token URL -> accept creates user with assigned role
10. `GET /api/v1/users` returns paginated, tenant-scoped results
11. System roles (SUPER_ADMIN, AGENCY_ADMIN, etc.) are seeded and non-deletable
12. Password reset: request silently succeeds (no email existence leak), confirm validates token and updates password
13. Super admin can list/view/suspend tenants
14. Hard delete of users with protection against self-delete and last-admin-delete

## Tests
| Test Class | Type | Test Count | Key Scenarios |
|-----------|------|-----------|--------------|
| `TenantRegistrationServiceTest` | Unit | 4 | Successful registration, duplicate email, event publishing, subscription stub called |
| `TenantServiceTest` | Unit | 4 | Get tenant, not found, update tenant, getTenantById not found |
| `AuthServiceTest` | Unit | 7 | Login (valid, wrong password, nonexistent, disabled), refresh (valid, revoked), logout |
| `UserServiceTest` | Unit | 9 | CRUD, delete (self/last-admin prevention), status change, current user |
| `InvitationServiceTest` | Unit | 6 | Invite (valid, duplicate email, pending duplicate), accept (valid, expired), revoke |
| `PasswordResetServiceTest` | Unit | 5 | Request (existing, unknown email), confirm (valid, expired, used) |
| `PmsPermissionEvaluatorTest` | Unit | 4 | hasPermission (present/absent), hasAnyPermission (match/none) |
| `AuthControllerTest` | Controller | 6 | Register, login, refresh, logout, validation errors |
| `TenantControllerTest` | Controller | 2 | GET/PUT tenant |
| `AdminTenantControllerTest` | Controller | 2 | List tenants, update status |
| `UserControllerTest` | Controller | 5 | List, get, me, update, delete |
| `InvitationControllerTest` | Controller | 4 | Invite, list, accept, revoke |
| `TenantRepositoryIntegrationTest` | Integration | 6 | findBySlug, existsBySlug, existsByContactEmail, findAllByStatus, timestamps |
| `UserRepositoryIntegrationTest` | Integration | 7 | findByEmail, fetchRolesAndPermissions, **tenant isolation**, countByRoleName |
| `InvitationRepositoryIntegrationTest` | Integration | 6 | findByToken, **tenant isolation**, findByEmailAndStatus, timestamps |
| **Total** | | **77** | |

## Definition of Done
- [x] All acceptance criteria pass
- [x] All 167 tests pass (`mvn test` — unit + controller tests)
- [x] Integration tests created (require Docker for `mvn verify`)
- [x] Full auth flow: register -> login -> use token -> refresh -> logout
- [x] Tenant isolation verified via integration test
- [x] Swagger docs annotations on all endpoints
- [x] No compiler warnings
- [x] Task documentation updated with design decisions
