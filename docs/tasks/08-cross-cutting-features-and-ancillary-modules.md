# Phase 8: Cross-Cutting Features and Ancillary Modules

## Prerequisites
- Phase 7 complete (all business modules implemented)
- All domain events from Phases 5-7 are being published

## Scope
- **Modules:** Module 14 (Audit Trail), Module 16 (Notifications), Webhooks, Module 11 (Owner Portal)
- **In scope:** Audit logging, notification dispatch (email/in-app/SMS), outbound webhooks, owner portal data proxy
- **Out of scope:** Reporting & Analytics (Module 10), Search (Module 13), Direct Booking Website (Module 12), Data Import (Module 15) -- these can be separate phases

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V7.0__create_audit_log_entries_table.sql` | `audit_log_entries` | Tenant-scoped. action, entityType, entityId, userId, changes (JSONB), ipAddress |
| `V7.1__create_notifications_table.sql` | `notifications` | Tenant-scoped. userId, type (EMAIL/IN_APP/SMS), channel, subject, body, status, sentAt |
| `V7.2__create_webhook_subscriptions_table.sql` | `webhook_subscriptions` | Tenant-scoped. url, events (array), secret, active |
| `V7.3__create_webhook_delivery_logs_table.sql` | `webhook_delivery_logs` | FK to subscription. statusCode, responseBody, attemptCount |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `AuditLogEntry` | Yes | id, tenantId, userId, action, entityType, entityId, changes (JSONB), ipAddress, userAgent, createdAt |
| `Notification` | Yes | id, tenantId, userId, type, channel, subject, body, status (PENDING/SENT/FAILED), sentAt, metadata (JSONB) |
| `WebhookSubscription` | Yes | id, tenantId, url, events (List<String>), secret, active, description |
| `WebhookDeliveryLog` | Yes | id, subscriptionId, eventType, payload, statusCode, responseBody, attemptCount, lastAttemptAt |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `GET` | `/api/v1/audit-logs` | Pageable + filters (entityType, action, userId, dateRange) | `PageResponse<AuditLogResponse>` | `AUDIT_VIEW` |
| `GET` | `/api/v1/notifications` | Pageable + filters (type, status) | `PageResponse<NotificationResponse>` | Authenticated |
| `PATCH` | `/api/v1/notifications/{id}/read` | -- | `NotificationResponse` | Authenticated |
| `GET` | `/api/v1/notifications/unread-count` | -- | `{ count: N }` | Authenticated |
| `GET` | `/api/v1/webhooks` | -- | `List<WebhookSubscriptionResponse>` | `WEBHOOK_MANAGE` |
| `POST` | `/api/v1/webhooks` | `CreateWebhookRequest` | `WebhookSubscriptionResponse` (201) | `WEBHOOK_MANAGE` |
| `PUT` | `/api/v1/webhooks/{id}` | `UpdateWebhookRequest` | `WebhookSubscriptionResponse` | `WEBHOOK_MANAGE` |
| `DELETE` | `/api/v1/webhooks/{id}` | -- | 204 | `WEBHOOK_MANAGE` |
| `GET` | `/api/v1/webhooks/{id}/deliveries` | Pageable | `PageResponse<WebhookDeliveryLogResponse>` | `WEBHOOK_MANAGE` |
| `GET` | `/api/v1/owner/properties` | -- | `List<PropertyResponse>` | `OWNER` role |
| `GET` | `/api/v1/owner/properties/{id}/bookings` | Pageable | `PageResponse<BookingResponse>` | `OWNER` role |
| `GET` | `/api/v1/owner/statements` | Pageable | `PageResponse<OwnerStatementResponse>` | `OWNER` role |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| *(Consumed)* All domain events | All modules | `AuditEventListener` -- logs all auditable events |
| *(Consumed)* `BookingCreatedEvent`, `PaymentReceivedEvent`, etc. | Various | `NotificationService` -- dispatches to relevant users |
| *(Consumed)* All configured events | Various | `WebhookDispatchService` -- sends to subscribed webhook URLs |

### Services
| Service | Key Methods |
|---------|------------|
| `AuditEventListener` | `@Async @EventListener` -- listens to `AuditEvent`, saves `AuditLogEntry` to DB. Must propagate tenant context via `TenantAwareTaskDecorator`. |
| `NotificationService` | `send(userId, type, subject, body)` -- central dispatcher. Routes to email (SES/MailHog), in-app (DB save), SMS (Twilio) based on notification type and user preferences. |
| `EmailService` | `sendEmail(to, subject, body, templateName)` -- Thymeleaf template rendering + Spring Mail |
| `WebhookSubscriptionService` | `create()`, `update()`, `delete()`, `getByTenant()` |
| `WebhookDispatchService` | `@Async` -- listens to domain events, matches against tenant's webhook subscriptions, sends HTTP POST with HMAC-SHA256 signature. Retries on failure (3 attempts with exponential backoff). |
| `OwnerPortalService` | `getProperties(ownerId)`, `getBookings(ownerId, propertyId)`, `getStatements(ownerId)` -- secure proxy that filters data strictly by `property.ownerId` |

### Webhook HMAC-SHA256 Signature
```
// For each webhook delivery:
String payload = objectMapper.writeValueAsString(event);
String signature = HmacUtils.hmacSha256Hex(subscription.getSecret(), payload);

// HTTP POST headers:
Content-Type: application/json
X-Webhook-Signature: sha256={signature}
X-Webhook-Event: booking.created
X-Webhook-Timestamp: {unix epoch}
```

### Audit Listener (Async)
```java
@Async
@EventListener
public void onAuditEvent(AuditEvent event) {
    // TenantContext is propagated via TenantAwareTaskDecorator
    AuditLogEntry entry = AuditLogEntry.builder()
        .tenantId(TenantContext.getTenantId())
        .userId(event.userId())
        .action(event.action())
        .entityType(event.entityType())
        .entityId(event.entityId())
        .changes(event.changes())
        .ipAddress(event.ipAddress())
        .build();
    auditLogRepository.save(entry);
}
```

## Acceptance Criteria
1. `AuditEventListener` runs asynchronously (`@Async`) and saves audit entries to DB
2. `AuditEventListener` preserves tenant context in async thread (via `TenantAwareTaskDecorator`)
3. `NotificationService` routes to correct channel (email/in-app/SMS) based on notification type
4. Email notifications render Thymeleaf templates and send via Spring Mail
5. In-app notifications saved to DB with PENDING status, marked as SENT
6. Unread notification count endpoint returns correct count for current user
7. Webhook dispatch sends HTTP POST with HMAC-SHA256 signature in `X-Webhook-Signature` header
8. Webhook dispatch retries failed deliveries (3 attempts, exponential backoff)
9. Webhook delivery logs record status code, response body, attempt count
10. Owner portal returns ONLY properties owned by the current user (strict `ownerId` filter)
11. Owner portal returns ONLY bookings for owned properties
12. Owner portal returns ONLY statements for owned properties
13. All entities tenant-scoped

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `AuditEventListenerTest` | Unit | Event processed, tenant context preserved, entry saved |
| `NotificationServiceTest` | Unit | Route to email, route to in-app, route to SMS |
| `EmailServiceTest` | Unit | Template rendered, email sent via Spring Mail |
| `WebhookDispatchServiceTest` | Unit | Signature generation, HTTP POST sent, retry on failure, max retries exceeded |
| `OwnerPortalServiceTest` | Unit | Returns only owned properties, rejects access to non-owned |
| `AuditLogRepositoryIntegrationTest` | Integration | Tenant isolation, filter by entityType/action |
| `NotificationRepositoryIntegrationTest` | Integration | Unread count, tenant isolation |
| `WebhookControllerIntegrationTest` | Integration | CRUD subscription, delivery log retrieval |
| `OwnerPortalControllerIntegrationTest` | Integration | Owner can only see own data, non-owner gets 403 |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Audit log captures events asynchronously without blocking business transactions
- Webhook signature verification works (can be tested with a simple HTTP listener)
- Owner portal strictly filters data -- no data leakage to non-owners
- Swagger docs generated for all endpoints
- No compiler warnings
