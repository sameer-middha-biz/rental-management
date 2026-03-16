# Phase 4: Property and Subscription Modules

## Prerequisites
- Phase 3 complete (Tenant, User, Auth flow, RBAC)

## Scope
- **Modules:** Module 2 (Property Management), Module 7 (Subscription & Billing)
- **In scope:** Properties, photos, amenities, tags, groups, subscription plans, plan enforcement
- **Out of scope:** Stripe billing integration (just local plan enforcement), property availability calendar (Phase 5)

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V3.0__create_subscription_plans_table.sql` | `subscription_plans` | NOT tenant-scoped. Seed: Starter (5 props), Pro (25 props), Agency (unlimited) |
| `V3.1__create_subscriptions_table.sql` | `subscriptions` | Tenant-scoped. One active subscription per tenant |
| `V3.2__create_properties_table.sql` | `properties` | Tenant-scoped. All columns per ARCHITECTURE.md Section 6 |
| `V3.3__create_property_photos_table.sql` | `property_photos` | Tenant-scoped. s3Key, displayOrder, isPrimary |
| `V3.4__create_amenities_table.sql` | `amenities`, `property_amenities` | Amenities + join table |
| `V3.5__create_property_tags_groups_tables.sql` | `property_tags`, `property_groups`, join tables | Organization entities |
| `V3.6__seed_subscription_plans.sql` | *(seed data)* | Insert Starter, Pro, Agency plans with limits |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `SubscriptionPlan` | No (global) | id, name, maxProperties, maxUsers, monthlyPriceMinorUnits, features (JSONB) |
| `Subscription` | Yes | id, tenantId, planId, status, startDate, endDate, stripeSubscriptionId |
| `Property` | Yes | id, tenantId, name, slug, address, city, country, lat/lng, propertyType, bedrooms, bathrooms, maxGuests, basePricePerNight, currency, status, description, checkInTime, checkOutTime |
| `PropertyPhoto` | Yes | id, propertyId, s3Key, displayOrder, isPrimary, caption |
| `Amenity` | No (global) | id, name, category, iconKey |
| `PropertyTag` | Yes | id, tenantId, name, color |
| `PropertyGroup` | Yes | id, tenantId, name, description |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `GET` | `/api/v1/properties` | Pageable + filters | `PageResponse<PropertyResponse>` | `PROPERTY_VIEW` |
| `GET` | `/api/v1/properties/{id}` | -- | `PropertyResponse` | `PROPERTY_VIEW` |
| `POST` | `/api/v1/properties` | `CreatePropertyRequest` | `PropertyResponse` (201) | `PROPERTY_CREATE` |
| `PUT` | `/api/v1/properties/{id}` | `UpdatePropertyRequest` | `PropertyResponse` | `PROPERTY_EDIT` |
| `DELETE` | `/api/v1/properties/{id}` | -- | 204 | `PROPERTY_DELETE` |
| `POST` | `/api/v1/properties/{id}/photos/upload-url` | `PhotoUploadRequest` | `PreSignedUrlResponse` | `PROPERTY_EDIT` |
| `DELETE` | `/api/v1/properties/{id}/photos/{photoId}` | -- | 204 | `PROPERTY_EDIT` |
| `PUT` | `/api/v1/properties/{id}/photos/reorder` | `ReorderPhotosRequest` | 200 | `PROPERTY_EDIT` |
| `GET` | `/api/v1/subscription` | -- | `SubscriptionResponse` | `SUBSCRIPTION_VIEW` |
| `GET` | `/api/v1/subscription/plans` | -- | `List<SubscriptionPlanResponse>` | Public |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `PropertyCreatedEvent` | `PropertyService` | (future: search index, channel sync) |
| `PropertyUpdatedEvent` | `PropertyService` | (future: channel sync) |

### Services
| Service | Key Methods |
|---------|------------|
| `SubscriptionService` | `createStarterSubscription(tenantId)`, `getCurrentSubscription()`, `getPlans()` |
| `PlanEnforcementService` | `checkPropertyLimit(tenantId)` -- throws `TenantLimitExceededException` if at limit |
| `PropertyService` | `create()` (calls PlanEnforcementService first), `update()`, `delete()`, `getById()`, `getAll()` |
| `PropertyPhotoService` | `generateUploadUrl()` (pre-signed S3 PUT URL), `deletePhoto()`, `reorderPhotos()` |

## Acceptance Criteria
1. Creating a property when tenant is at plan limit returns 409 with error code `TENANT.LIMIT.EXCEEDED`
2. Creating a property when under plan limit succeeds and returns 201
3. Starter plan allows max 5 properties; Pro allows 25; Agency allows unlimited
4. `PropertyPhotoService.generateUploadUrl()` returns a valid pre-signed S3 PUT URL
5. Properties are tenant-scoped: Tenant A cannot see Tenant B's properties
6. Property CRUD endpoints return correct HTTP status codes (201, 200, 204)
7. Deleting a property soft-deletes (status change) or cascades photo cleanup
8. Photo reorder updates `displayOrder` for all photos in a single transaction
9. `GET /api/v1/properties` supports pagination and filtering by status, propertyType
10. Subscription plans are seeded on Flyway migration

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `PlanEnforcementServiceTest` | Unit | Under limit allows, at limit throws, unlimited plan always allows |
| `PropertyServiceTest` | Unit | Create with plan check, update, delete, CRUD operations |
| `PropertyPhotoServiceTest` | Unit | Generate pre-signed URL, delete photo, reorder |
| `SubscriptionServiceTest` | Unit | Create starter, get current, plan lookup |
| `PropertyRepositoryIntegrationTest` | Integration | Tenant isolation, pagination, filtering |
| `PropertyControllerIntegrationTest` | Integration | Full CRUD flow, plan enforcement via API, photo upload URL generation |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Plan enforcement blocks property creation at limit
- S3 pre-signed URLs generated correctly (tested against LocalStack)
- Swagger docs generated for all endpoints
- No compiler warnings
