# Phase 6: Payment and Channel Sync Modules

## Prerequisites
- Phase 5 complete (Booking, Guest, BookingCreatedEvent, BookingCancelledEvent)

## Scope
- **Modules:** Module 6 (Payments & Financial), Module 5 (Channel Management / OTA Sync)
- **In scope:** Stripe payment intents, webhook handling, invoices, owner statements, iCal sync, channel connections, calendar conflict detection
- **Out of scope:** Stripe subscription billing for SaaS plans (deferred), native OTA API integration (MVP uses iCal only)

**Architectural constraint:** `booking` module does NOT depend on `channel`. Communication is via domain events only. `channel` depends on `booking` (for reading booking data).

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V5.0__create_payments_table.sql` | `payments` | Tenant-scoped. stripePaymentIntentId, amount, currency, status |
| `V5.1__create_invoices_table.sql` | `invoices` | Tenant-scoped. FK to booking, line items |
| `V5.2__create_owner_statements_table.sql` | `owner_statements` | Tenant-scoped. Period-based, FK to property owner |
| `V5.3__create_channel_connections_table.sql` | `channel_connections` | Tenant-scoped. channelType, icalUrl, propertyId, syncStatus, consecutiveFailures |
| `V5.4__create_channel_sync_logs_table.sql` | `channel_sync_logs` | Tenant-scoped. Per-sync execution log |
| `V5.5__create_calendar_conflicts_table.sql` | `calendar_conflicts` | Tenant-scoped. Detected overlapping bookings across channels |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `Payment` | Yes | id, tenantId, bookingId, stripePaymentIntentId, amountMinorUnits, currency, status, paidAt |
| `Invoice` | Yes | id, tenantId, bookingId, invoiceNumber, totalMinorUnits, status, issuedAt, dueAt |
| `OwnerStatement` | Yes | id, tenantId, propertyId, ownerId, periodStart, periodEnd, grossRevenue, managementFee, netPayout, status |
| `ChannelConnection` | Yes | id, tenantId, propertyId, channelType (AIRBNB/BOOKING_COM/OTHER), icalUrl, syncDirection, lastSyncAt, syncStatus, consecutiveFailures |
| `ChannelSyncLog` | Yes | id, connectionId, syncedAt, eventsImported, status, errorMessage |
| `CalendarConflict` | Yes | id, tenantId, propertyId, localBookingId, externalEventSummary, overlapStart, overlapEnd, resolvedAt |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `POST` | `/api/v1/bookings/{id}/payments` | `CreatePaymentRequest` | `PaymentResponse` (201) | `PAYMENT_CREATE` |
| `GET` | `/api/v1/bookings/{id}/payments` | -- | `List<PaymentResponse>` | `PAYMENT_VIEW` |
| `POST` | `/api/v1/webhooks/stripe` | *(Stripe payload)* | 200 | Public (verified by signature) |
| `GET` | `/api/v1/invoices` | Pageable + filters | `PageResponse<InvoiceResponse>` | `INVOICE_VIEW` |
| `GET` | `/api/v1/owner-statements` | Pageable + filters | `PageResponse<OwnerStatementResponse>` | `OWNER_STATEMENT_VIEW` |
| `GET` | `/api/v1/channels` | -- | `List<ChannelConnectionResponse>` | `CHANNEL_VIEW` |
| `POST` | `/api/v1/channels` | `CreateChannelConnectionRequest` | `ChannelConnectionResponse` (201) | `CHANNEL_MANAGE` |
| `DELETE` | `/api/v1/channels/{id}` | -- | 204 | `CHANNEL_MANAGE` |
| `POST` | `/api/v1/channels/{id}/sync` | -- | `ChannelSyncLogResponse` | `CHANNEL_MANAGE` |
| `GET` | `/api/v1/channels/conflicts` | Pageable | `PageResponse<CalendarConflictResponse>` | `CHANNEL_VIEW` |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `PaymentReceivedEvent` | `StripePaymentService` (webhook) | Booking (update status to CONFIRMED), Notification |
| `PaymentFailedEvent` | `StripePaymentService` (webhook) | Notification |
| *(Consumed)* `BookingCreatedEvent` | Booking module | `ChannelEventListener` -- triggers cross-channel date blocking |
| *(Consumed)* `BookingCancelledEvent` | Booking module | `ChannelEventListener` -- unblocks dates on other channels |

### Services
| Service | Key Methods |
|---------|------------|
| `StripePaymentService` | `createPaymentIntent(bookingId, amount)`, `handleWebhook(payload, signature)` -- verifies Stripe signature, processes payment_intent.succeeded/failed |
| `InvoiceService` | `generateInvoice(bookingId)`, `getInvoices()` |
| `OwnerStatementService` | `generateStatement(propertyId, period)`, `getStatements()` |
| `ChannelConnectionService` | `create()`, `delete()`, `triggerSync()` |
| `ICalPollingScheduler` | `@Scheduled(fixedDelay)` + `@SchedulerLock` -- polls all active iCal URLs, imports events, detects conflicts |
| `ICalSyncService` | `syncConnection(connectionId)` -- fetches iCal, parses with ical4j, creates/updates bookings, detects conflicts |
| `ChannelEventListener` | Listens to `BookingCreatedEvent` / `BookingCancelledEvent` -- triggers cross-channel date blocking/unblocking |

### iCal Polling Flow
```
@Scheduled(fixedDelay = 300000)  // Every 5 minutes
@SchedulerLock(name = "ical-poll", lockAtLeastFor = "PT4M", lockAtMostFor = "PT10M")
void pollAllConnections() {
  List<ChannelConnection> active = repository.findByStatusActive();
  for (connection : active) {
    // Per-connection Redis SETNX lock to prevent concurrent sync of same connection
    if (redisLock.tryLock("ical-sync:" + connection.getId(), Duration.ofMinutes(5))) {
      try {
        icalSyncService.syncConnection(connection.getId());
      } finally {
        redisLock.unlock("ical-sync:" + connection.getId());
      }
    }
  }
}
```

### Stripe Webhook Flow
```
POST /api/v1/webhooks/stripe
  -> Verify Stripe-Signature header (HMAC-SHA256 with webhook secret)
  -> If invalid signature -> 400
  -> Parse event type:
     -> payment_intent.succeeded -> update Payment status, publish PaymentReceivedEvent
     -> payment_intent.payment_failed -> update Payment status, publish PaymentFailedEvent
  -> Return 200 (idempotent: check if already processed by Stripe event ID)
```

## Acceptance Criteria
1. `StripePaymentService.createPaymentIntent()` creates a Stripe PaymentIntent with correct amount and currency
2. Stripe webhook endpoint verifies signature before processing (rejects invalid signatures with 400)
3. Webhook processing is idempotent (duplicate Stripe event IDs are ignored)
4. `PaymentReceivedEvent` published on successful payment
5. iCal polling runs on schedule with ShedLock (only one instance executes)
6. Per-connection Redis lock prevents concurrent sync of same channel connection
7. iCal sync parses VEVENT entries and creates bookings or detects conflicts
8. `ChannelEventListener` reacts to `BookingCreatedEvent` to block dates on connected channels
9. `booking` module has zero imports from `channel` module (verified by code review)
10. Calendar conflicts are detected and stored when imported dates overlap existing bookings
11. All entities tenant-scoped: channel connections, payments, invoices isolated per tenant

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `StripePaymentServiceTest` | Unit | Create intent, webhook valid signature, webhook invalid signature, idempotent processing |
| `ICalSyncServiceTest` | Unit | Parse iCal feed, import events, detect conflicts, handle malformed feeds |
| `ICalPollingSchedulerTest` | Unit | ShedLock annotation present, Redis lock acquired/released |
| `ChannelEventListenerTest` | Unit | Reacts to BookingCreatedEvent, BookingCancelledEvent |
| `InvoiceServiceTest` | Unit | Generate invoice with correct line items |
| `PaymentRepositoryIntegrationTest` | Integration | Tenant isolation |
| `ChannelConnectionRepositoryIntegrationTest` | Integration | Tenant isolation |
| `StripeWebhookControllerIntegrationTest` | Integration | Valid webhook processed, invalid signature rejected |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- No circular dependencies between booking and channel (booking has zero channel imports)
- Stripe webhook endpoint works with Stripe CLI test mode
- iCal sync tested with sample .ics files
- Swagger docs generated for all endpoints
- No compiler warnings
