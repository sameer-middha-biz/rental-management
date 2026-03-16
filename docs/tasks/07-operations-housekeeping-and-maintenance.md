# Phase 7: Housekeeping and Maintenance Modules

## Prerequisites
- Phase 5 complete (Booking, BookingStatusChangedEvent)
- Phase 4 complete (Property)

## Scope
- **Modules:** Module 8 (Housekeeping & Cleaning Management), Module 9 (Maintenance & Issue Tracking)
- **In scope:** Cleaning tasks, checklist templates, auto-creation on checkout, maintenance issues, photo uploads, assignment, status transitions
- **Out of scope:** Mobile app views (post-MVP), contractor portal (post-MVP)

## Deliverables

### Database Migrations
| File | Tables | Notes |
|------|--------|-------|
| `V6.0__create_cleaning_checklist_templates_table.sql` | `cleaning_checklist_templates`, `cleaning_checklist_template_items` | Tenant-scoped. Reusable templates per property |
| `V6.1__create_cleaning_tasks_table.sql` | `cleaning_tasks`, `cleaning_checklist_items` | Tenant-scoped. FK to booking, property, assigned user |
| `V6.2__create_maintenance_issues_table.sql` | `maintenance_issues` | Tenant-scoped. FK to property, reported by, assigned to |
| `V6.3__create_maintenance_issue_photos_table.sql` | `maintenance_issue_photos` | Tenant-scoped. s3Key per photo |

### Entities
| Entity | Tenant-Scoped | Key Fields |
|--------|--------------|------------|
| `CleaningChecklistTemplate` | Yes | id, tenantId, propertyId (nullable = applies to all), name, items |
| `CleaningChecklistTemplateItem` | Yes | id, templateId, description, displayOrder |
| `CleaningTask` | Yes | id, tenantId, propertyId, bookingId, assignedUserId, status (PENDING/IN_PROGRESS/COMPLETED/SKIPPED), scheduledDate, completedAt, notes |
| `CleaningChecklistItem` | Yes | id, taskId, description, completed, completedAt, displayOrder |
| `MaintenanceIssue` | Yes | id, tenantId, propertyId, reportedById, assignedToId, title, description, priority (LOW/MEDIUM/HIGH/URGENT), status (OPEN/IN_PROGRESS/RESOLVED/CLOSED), resolvedAt |
| `MaintenanceIssuePhoto` | Yes | id, issueId, s3Key, caption |

### API Endpoints
| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `GET` | `/api/v1/cleaning-tasks` | Pageable + filters (status, property, date) | `PageResponse<CleaningTaskResponse>` | `CLEANING_VIEW` |
| `GET` | `/api/v1/cleaning-tasks/{id}` | -- | `CleaningTaskResponse` | `CLEANING_VIEW` |
| `POST` | `/api/v1/cleaning-tasks` | `CreateCleaningTaskRequest` | `CleaningTaskResponse` (201) | `CLEANING_MANAGE` |
| `PATCH` | `/api/v1/cleaning-tasks/{id}/status` | `UpdateStatusRequest` | `CleaningTaskResponse` | `CLEANING_MANAGE` |
| `PATCH` | `/api/v1/cleaning-tasks/{id}/assign` | `AssignTaskRequest` | `CleaningTaskResponse` | `CLEANING_MANAGE` |
| `PATCH` | `/api/v1/cleaning-tasks/{id}/checklist/{itemId}` | `UpdateChecklistItemRequest` | `CleaningChecklistItemResponse` | `CLEANING_MANAGE` |
| `GET` | `/api/v1/cleaning-templates` | Pageable | `PageResponse<CleaningTemplateResponse>` | `CLEANING_VIEW` |
| `POST` | `/api/v1/cleaning-templates` | `CreateCleaningTemplateRequest` | `CleaningTemplateResponse` (201) | `CLEANING_MANAGE` |
| `GET` | `/api/v1/maintenance-issues` | Pageable + filters (status, priority, property) | `PageResponse<MaintenanceIssueResponse>` | `MAINTENANCE_VIEW` |
| `GET` | `/api/v1/maintenance-issues/{id}` | -- | `MaintenanceIssueResponse` | `MAINTENANCE_VIEW` |
| `POST` | `/api/v1/maintenance-issues` | `CreateMaintenanceIssueRequest` | `MaintenanceIssueResponse` (201) | `MAINTENANCE_CREATE` |
| `PUT` | `/api/v1/maintenance-issues/{id}` | `UpdateMaintenanceIssueRequest` | `MaintenanceIssueResponse` | `MAINTENANCE_EDIT` |
| `PATCH` | `/api/v1/maintenance-issues/{id}/status` | `UpdateStatusRequest` | `MaintenanceIssueResponse` | `MAINTENANCE_EDIT` |
| `PATCH` | `/api/v1/maintenance-issues/{id}/assign` | `AssignIssueRequest` | `MaintenanceIssueResponse` | `MAINTENANCE_EDIT` |
| `POST` | `/api/v1/maintenance-issues/{id}/photos/upload-url` | `PhotoUploadRequest` | `PreSignedUrlResponse` | `MAINTENANCE_EDIT` |

### Domain Events
| Event | Published By | Consumed By |
|-------|-------------|-------------|
| *(Consumed)* `BookingStatusChangedEvent` (CHECKED_OUT) | Booking module | `CleaningAutoCreationService` -- auto-creates cleaning task |
| `CleaningTaskCompletedEvent` | `CleaningTaskService` | (future: notification to manager) |
| `MaintenanceIssueCreatedEvent` | `MaintenanceIssueService` | (future: notification to assigned user) |

### Services
| Service | Key Methods |
|---------|------------|
| `CleaningTaskService` | `create()`, `updateStatus()`, `assign()`, `updateChecklistItem()` |
| `CleaningAutoCreationService` | `@EventListener` for `BookingStatusChangedEvent` where status = CHECKED_OUT. Creates task from property's template (or default). |
| `CleaningTemplateService` | `create()`, `getTemplates()`, `getByProperty()` |
| `MaintenanceIssueService` | `create()`, `update()`, `updateStatus()`, `assign()` |

### Auto-Creation Flow
```
BookingStatusChangedEvent { bookingId, propertyId, newStatus: CHECKED_OUT }
  -> CleaningAutoCreationService.onBookingCheckedOut(event)
     -> Find CleaningChecklistTemplate for propertyId (or default template)
     -> Create CleaningTask {
          propertyId, bookingId,
          status: PENDING,
          scheduledDate: booking.checkOut (same day)
        }
     -> Copy template items -> CleaningChecklistItems (all unchecked)
     -> Save
     -> (Future: send notification to assigned housekeeper)
```

## Acceptance Criteria
1. `BookingStatusChangedEvent(CHECKED_OUT)` triggers auto-creation of `CleaningTask` with checklist from template
2. If property has a specific template, that template is used; otherwise default template applies
3. Cleaning task status transitions: PENDING -> IN_PROGRESS -> COMPLETED (or SKIPPED)
4. Checklist items can be individually marked as completed
5. Maintenance issue supports photo uploads via pre-signed S3 URLs
6. Maintenance issue status transitions: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
7. Assignment of cleaning tasks and maintenance issues to specific users
8. All entities tenant-scoped: data isolated between tenants
9. Filtering by status, priority, property, and date works correctly
10. `HOUSEKEEPER` role can view and update assigned cleaning tasks only

## Tests Required
| Test Class | Type | Key Scenarios |
|-----------|------|--------------|
| `CleaningAutoCreationServiceTest` | Unit | Event triggers task creation, template items copied, no template falls back to default |
| `CleaningTaskServiceTest` | Unit | Create, status transitions (valid and invalid), assign, update checklist item |
| `MaintenanceIssueServiceTest` | Unit | Create, update, status transitions, assign, photo URL generation |
| `CleaningTaskRepositoryIntegrationTest` | Integration | Tenant isolation, filter by status/property/date |
| `MaintenanceIssueRepositoryIntegrationTest` | Integration | Tenant isolation, filter by status/priority |
| `CleaningTaskControllerIntegrationTest` | Integration | CRUD flow, HOUSEKEEPER role access |

## Definition of Done
- All acceptance criteria pass
- All tests pass (`mvn clean verify`)
- Auto-creation triggered by booking checkout event
- HOUSEKEEPER role can only see assigned tasks
- Swagger docs generated for all endpoints
- No compiler warnings
