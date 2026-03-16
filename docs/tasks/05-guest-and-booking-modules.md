# Phase 5: Guest and Booking Modules

## Prerequisites
- Phase 4 complete (Property, Subscription, PlanEnforcementService)

## Scope
- **Modules:** Module 4 (Guest Management), Module 3 (Booking & Reservation Management)
- **In scope:** Guest CRUD + GDPR erasure, booking CRUD, availability locking, pricing engine, seasonal rates, coupons
- **Out of scope:** Channel sync (Phase 6), payment processing (Phase 6), direct booking website (Phase 8)

**This is the most complex phase.** The booking creation flow involves concurrent-safe availability locking, dynamic pricing, and event publishing in a single atomic transaction.

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V4.0__create_guests_table.sql` | `guests` | Tenant-scoped. Encrypted fields: email, phone (via EncryptedStringConverter) |
| `V4.1__create_bookings_table.sql` | `bookings` | Tenant-scoped. Status enum, FK to property + guest |
| `V4.2__create_booking_nightly_rates_table.sql` | `booking_nightly_rates` | Per-night price breakdown |
| `V4.3__create_seasonal_rates_table.sql` | `seasonal_rates` | Tenant-scoped. Per-property date ranges with prices |
| `V4.4__create_pricing_rules_table.sql` | `pricing_rules` | Tenant-scoped. Priority-ordered rules (min stay discount, last-minute, etc.) |
| `V4.5__create_coupons_table.sql` | `coupons` | Tenant-scoped. Code, discount type, value, validity |
| `V4.6__create_availability_index.sql` | *(index only)* | Partial index on `bookings` for availability checks: `WHERE status NOT IN ('CANCELLED', 'DECLINED')` |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `Guest` | Yes | id, tenantId, firstName, lastName, email (encrypted), phone (encrypted), nationality, idDocumentS3Key, notes, totalBookings, lastStayAt |
| `Booking` | Yes | id, tenantId, propertyId, guestId, checkIn, checkOut, status, guestCount, totalPriceMinorUnits, currency, source, notes, specialRequests |
| `BookingNightlyRate` | Yes | id, bookingId, date, rateMinorUnits, rateName |
| `SeasonalRate` | Yes | id, tenantId, propertyId, name, startDate, endDate, pricePerNightMinorUnits, minStay |
| `PricingRule` | Yes | id, tenantId, propertyId, ruleType, priority, discountPercent, conditions (JSONB) |
| `Coupon` | Yes | id, tenantId, code, discountType (PERCENTAGE/FIXED), discountValue, validFrom, validUntil, maxUses, currentUses |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `GET` | `/api/v1/guests` | Pageable + search | `PageResponse<GuestResponse>` | `GUEST_VIEW` |
| `GET` | `/api/v1/guests/{id}` | -- | `GuestResponse` | `GUEST_VIEW` |
| `POST` | `/api/v1/guests` | `CreateGuestRequest` | `GuestResponse` (201) | `GUEST_CREATE` |
| `PUT` | `/api/v1/guests/{id}` | `UpdateGuestRequest` | `GuestResponse` | `GUEST_EDIT` |
| `DELETE` | `/api/v1/guests/{id}/gdpr` | -- | 204 | `GUEST_DELETE` |
| `GET` | `/api/v1/bookings` | Pageable + filters | `PageResponse<BookingResponse>` | `BOOKING_VIEW` |
| `GET` | `/api/v1/bookings/{id}` | -- | `BookingResponse` | `BOOKING_VIEW` |
| `POST` | `/api/v1/bookings` | `CreateBookingRequest` | `BookingResponse` (201) | `BOOKING_CREATE` |
| `PUT` | `/api/v1/bookings/{id}` | `UpdateBookingRequest` | `BookingResponse` | `BOOKING_EDIT` |
| `PATCH` | `/api/v1/bookings/{id}/status` | `UpdateBookingStatusRequest` | `BookingResponse` | `BOOKING_EDIT` |
| `GET` | `/api/v1/properties/{id}/availability` | dateFrom, dateTo | `AvailabilityResponse` | `PROPERTY_VIEW` |
| `POST` | `/api/v1/bookings/calculate-price` | `PriceCalculationRequest` | `PriceBreakdownResponse` | `BOOKING_CREATE` |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `BookingCreatedEvent` | `BookingService` | Channel module (Phase 6), notification module (Phase 8) |
| `BookingStatusChangedEvent` | `BookingService` | Housekeeping (Phase 7: auto-create cleaning on CHECKED_OUT), Channel (Phase 6) |
| `BookingCancelledEvent` | `BookingService` | Channel (Phase 6), Payment (Phase 6: refund) |
| `GuestCreatedEvent` | `GuestService` | (future: notifications) |

### Services
| Service | Key Methods |
|---------|------------|
| `GuestService` | `create()`, `update()`, `gdprErase(guestId)` -- anonymizes data + deletes S3 documents |
| `AvailabilityService` | `checkAndLock(propertyId, checkIn, checkOut)` -- uses `pg_advisory_xact_lock` to prevent double-booking |
| `PricingService` | `calculatePrice(propertyId, checkIn, checkOut, guestCount, couponCode)` -- applies seasonal rates, pricing rules, coupons in priority order |
| `BookingService` | `create()` -- atomic: lock -> calculate price -> save booking + nightly rates -> publish event |

### Booking Creation Flow (Critical Path)
```
POST /api/v1/bookings { propertyId, guestId, checkIn, checkOut, guestCount, couponCode? }
  -> BEGIN TRANSACTION
  -> AvailabilityService.checkAndLock(propertyId, checkIn, checkOut)
     -> SELECT pg_advisory_xact_lock(hash(propertyId + checkIn))
     -> Query bookings WHERE propertyId AND overlapping dates AND status NOT IN (CANCELLED, DECLINED)
     -> If overlap found -> throw ConflictException("BOOKING.AVAILABILITY.CONFLICT")
  -> PricingService.calculatePrice(propertyId, checkIn, checkOut, guestCount, couponCode)
     -> Load base price from Property
     -> Apply SeasonalRates for each night (override base price if date falls in range)
     -> Apply PricingRules in priority order (e.g., 7+ nights = 10% discount)
     -> Apply Coupon if valid
     -> Return PriceBreakdown { nightlyRates[], totalBeforeDiscount, discountAmount, totalAfterDiscount }
  -> Create Booking entity with status CONFIRMED
  -> Create BookingNightlyRate for each night
  -> BookingRepository.save(booking)
  -> Publish BookingCreatedEvent { bookingId, propertyId, tenantId, checkIn, checkOut }
  -> COMMIT TRANSACTION
  -> Return BookingResponse (201)
```

### GDPR Erasure Flow
```
DELETE /api/v1/guests/{id}/gdpr
  -> GuestService.gdprErase(guestId)
     -> Set firstName = "DELETED", lastName = "DELETED"
     -> Set email = "deleted-{uuid}@erased.local"
     -> Set phone = null, nationality = null, notes = null
     -> Delete S3 documents (idDocumentS3Key)
     -> Set idDocumentS3Key = null
     -> Save
     -> Publish AuditEvent("GDPR_ERASURE", guestId)
```

## Acceptance Criteria
1. Booking creation acquires `pg_advisory_xact_lock` to prevent concurrent double-bookings
2. Attempting to book overlapping dates on the same property returns 409 with `BOOKING.AVAILABILITY.CONFLICT`
3. Pricing engine applies seasonal rates per-night (overrides base price for dates in seasonal range)
4. Pricing engine applies pricing rules in priority order (lower priority number = applied first)
5. Coupon application reduces total correctly (percentage or fixed amount)
6. Invalid/expired/max-used coupon returns appropriate error
7. Booking creation is atomic: lock + price + save + event in single transaction
8. `BookingCreatedEvent` is published after successful booking save
9. GDPR erasure anonymizes all PII fields and deletes S3 documents
10. GDPR erasure publishes audit event
11. Guest email and phone fields are encrypted at rest (via `EncryptedStringConverter`)
12. All entities are tenant-scoped: cross-tenant data invisible
13. Availability check endpoint returns correct available/blocked dates for a property

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `AvailabilityServiceTest` | Unit | Available dates pass, overlapping dates throw, advisory lock called |
| `PricingServiceTest` | Unit | Base price only, seasonal rate override, pricing rule discount, coupon applied, multiple rules stacked |
| `BookingServiceTest` | Unit | Full create flow, event published, atomic transaction |
| `GuestServiceTest` | Unit | GDPR erasure anonymization, S3 deletion, audit event |
| `BookingRepositoryIntegrationTest` | Integration | Availability overlap query, tenant isolation, partial index used |
| `BookingControllerIntegrationTest` | Integration | Create booking end-to-end, double-booking rejection, price calculation |
| `GuestRepositoryIntegrationTest` | Integration | Encrypted field read/write, tenant isolation |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Double-booking prevented under concurrent requests
- Pricing engine correctly calculates for all scenarios
- GDPR erasure fully anonymizes guest data
- Swagger docs generated for all endpoints
- No compiler warnings
