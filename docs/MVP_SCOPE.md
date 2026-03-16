# Holiday Rental Management Software — MVP Scope

**Version:** 1.0
**Date:** March 2026
**Status:** Draft for Review

---

## Overview

This document defines the Minimum Viable Product (MVP) for a holiday rental management platform. The MVP covers the core capabilities required to deliver value to all three primary user segments — individual property owners, small property management agencies, and larger management companies — from day one.

The guiding principle of the MVP is: *launch fast, prove value, iterate*. Every feature included here is load-bearing. If it were removed, the product would not function as a credible rental management platform.

---

## 1. User Management & Roles

The platform must support distinct roles with appropriate access levels from launch.

**Roles:**

- **Super Admin** — full platform access, manages tenants/accounts (internal Franchimp staff use)
- **Agency Admin** — manages their agency's properties, staff, and owners
- **Property Manager** — day-to-day operational access across assigned properties
- **Property Owner** — read-only view of their own property portfolio, bookings, and financial statements
- **Housekeeper / Cleaner** — access limited to assigned cleaning tasks and schedules
- **Guest** — access to their own booking details and pre-arrival information

**Core capabilities:**

- Registration, login, and password reset via email
- Role-based access control (RBAC) enforced at the API layer
- Ability to invite team members via email with a pre-assigned role
- Two-factor authentication (2FA) for Admin roles

---

## 2. Property Management

The ability to create and manage a portfolio of rental properties is the foundation of the platform.

**Core capabilities:**

- Create, edit, and archive property listings with name, address, type (apartment, villa, cottage, etc.), and description
- Upload and manage a gallery of property photos (min. 20 images per property)
- Define amenities from a structured checklist (Wi-Fi, parking, pool, pets allowed, etc.) plus free-text additions
- Set maximum guest capacity, number of bedrooms, bathrooms, and beds
- Configure check-in and check-out times
- Add internal notes visible only to staff (not guests)
- Group properties by location, owner, or custom tag for easier bulk management
- Multi-property dashboard showing all properties at a glance with key status indicators

---

## 3. Booking & Reservation Management

**Core capabilities:**

- Unified availability calendar per property (month and multi-month views)
- Manual reservation creation by staff (with guest details, dates, pricing, and notes)
- Automatic block-out for maintenance, owner stays, and other non-guest use cases
- Booking statuses: Enquiry → Confirmed → Checked In → Checked Out → Cancelled
- Booking modification (change dates, update guest count) with conflict checking
- Automated confirmation email to guest on booking creation
- Cancellation policy configuration per property (flexible, moderate, strict) with automatic refund calculation rules
- Basic seasonal rate rules (peak, off-peak, shoulder) configurable per property or property group
- Weekend vs. weekday rate differentiation
- Length-of-stay discounts (weekly, monthly) configurable per property
- Per-guest surcharge beyond base occupancy (e.g., extra guest fee per night beyond 4 guests)
- Cleaning fee configuration: flat fee per booking, configurable per property
- Short-stay premium: ability to add a surcharge for stays below a configurable threshold (e.g., 1-2 night bookings)
- Last-minute and early-bird discount rules (percentage or flat amount, configurable by lead time)
- Promotional / coupon codes for direct bookings (percentage or flat discount)
- Tax and levy configuration per property or region: support for multiple tax types (VAT, tourist tax, city occupancy tax) as line items on guest invoices
- Rate preview calculator: staff can preview the total price breakdown (nightly rate × nights + fees + taxes) before confirming a booking
- Per-channel rate management: ability to set different base rates per OTA channel (e.g., direct bookings 10% cheaper to offset commission)
- Third-party dynamic pricing integration support: API hooks for PriceLabs, Beyond Pricing, and Wheelhouse to push rates into the platform

---

## 4. Guest Management

**Core capabilities:**

- Guest profile with contact details, booking history, and notes
- Secure storage of guest identification details (passport/ID upload) for compliance
- Automated pre-arrival email sequence (booking confirmation, check-in instructions, arrival guide)
- Automated post-departure email (thank you + review request)
- Unified omni-channel inbox: all guest messages from all connected channels (Airbnb, Booking.com, direct bookings, email) in a single interface
- Conversation history linked to the booking record
- Snooze, assign to team member, and label/tag conversations
- Mobile-ready inbox for on-the-go responses
- Manual responses only in MVP (AI-drafted replies deferred to post-MVP)
- Flag guests (VIP, do-not-rebook) with internal notes

---

## 5. Channel Management (OTA Sync)

This is one of the most critical MVP features. Hosts cannot manage a modern rental business without connecting to external platforms.

**Core capabilities:**

- Two-way calendar sync via iCal for all major OTAs (Airbnb, Booking.com, Vrbo, etc.)
- Native API integration with Airbnb (listing sync, booking push/pull, messaging)
- Native API integration with Booking.com (property sync, reservation sync)
- Automatic block of dates across all channels when a booking is created on any channel (preventing double-bookings)
- **Calendar conflict detection and resolution:**
  - When an API-synced booking conflicts with an existing reservation, reject the incoming booking in real-time and notify the property manager
  - When an iCal sync detects a conflict, flag it in the channel dashboard with both booking details and require manual resolution (keep one, cancel one, or relocate)
  - Conflict alert sent immediately via email and in-app notification to the property manager
  - Conflict log: maintain a record of all detected conflicts and their resolution for audit purposes
- Channel dashboard showing connection status, last sync time, and any sync errors
- Rate push to connected OTA channels (requires channel API support)
- iCal sync frequency: configurable per channel, default every 15 minutes, minimum every 5 minutes
- Channel error retry logic: automatic retry of failed syncs with exponential backoff, alert after 3 consecutive failures

> **Differentiator note:** iCal-only sync is table stakes. Native two-way API integrations for Airbnb and Booking.com at MVP gives a meaningful head start over lower-end competitors.

---

## 6. Payments & Financial Management

**Core capabilities:**

- Record and track payments against bookings (amount, method, date, status)
- Support for multiple payment statuses: Pending, Received, Partial, Refunded
- Integration with Stripe for online payment collection (card payments)
- Automated generation of guest invoices/receipts as PDF
- Security deposit management (charge, hold, release workflow)
- Basic owner statement generation — monthly breakdown of income, platform fees, and management fees per property per owner
- Export financial data to CSV for external accounting tools

### Subscription & Billing

The platform operates on a tiered subscription model enforced at the account (agency/tenant) level.

**Tiers:**

| Tier | Properties | Channel Sync | Direct Booking Page | Team Members | Owner Portal | Reporting |
|---|---|---|---|---|---|---|
| **Starter** (free) | 1 | ❌ | ❌ | 1 (owner only) | ❌ | Basic |
| **Pro** (paid) | Up to 10 | ✅ iCal only | ✅ | Up to 3 | ✅ | Full |
| **Agency** (paid) | Unlimited | ✅ iCal + native API | ✅ | Unlimited | ✅ | Full |

**Core capabilities:**

- Subscription plans stored and enforced at the tenant level; plan limits applied at the API layer (not just the UI)
- Integration with Stripe Subscriptions for recurring billing (monthly and annual billing cycles)
- 14-day free trial of the Pro plan for all new accounts; no credit card required to start trial
- Upgrade and downgrade self-service flow in account settings, with prorated billing on upgrades
- On downgrade: existing data (properties, bookings, history) is preserved in read-only mode for any resources exceeding the new plan's limits; new creation is blocked until within limits
- Trial expiry and payment failure handling: grace period of 3 days before feature access is restricted, with email notifications at expiry, day 1, and day 3 of grace period
- Subscription status visible in account settings: current plan, next billing date, payment method, invoice history
- Super Admin can view and manage subscription status for all tenant accounts from the platform admin console

---

## 7. Housekeeping & Cleaning Management

**Core capabilities:**

- Automatic creation of a cleaning task when a booking checks out (triggered by checkout date/time)
- Manual task creation for mid-stay cleans, deep cleans, or one-off tasks
- Assign cleaning tasks to individual housekeepers or a cleaning team
- Housekeeper mobile-friendly view showing assigned tasks with property address, access instructions, and task checklist
- Mark task as in-progress and complete from the housekeeper view
- Notify the property manager when a clean is marked complete
- Track cleaning history per property

---

## 8. Maintenance & Issue Tracking

**Core capabilities:**

- Log maintenance issues against a property (description, priority, photos)
- Assign issues to internal staff or an external contractor
- Issue statuses: Open → In Progress → Resolved
- Notify the assignee by email when an issue is assigned
- Maintenance history log per property

---

## 9. Reporting & Analytics

**Core capabilities:**

- Occupancy rate per property (monthly and annual view)
- Revenue per property (actual received, projected from confirmed bookings)
- Average daily rate (ADR) and revenue per available night (RevPAN)
- Booking source breakdown (which OTA or direct)
- Upcoming arrivals and departures report (7-day and 30-day view)
- Owner statements (exportable as PDF and CSV)
- All reports exportable as CSV

---

## 10. Owner Portal

Owners need visibility without operational access.

**Core capabilities:**

- Owners can log in and see their own properties only
- View live availability calendar for their properties
- View confirmed bookings (guest first name only for privacy in default view)
- View financial statements and revenue summaries
- Block dates on their own calendar (owner stays, holds)
- Download monthly statements as PDF

---

## 11. Direct Booking Website (Basic)

A lightweight, embeddable booking widget or simple hosted booking page is essential for any client wanting direct bookings (and avoiding OTA commission).

**Core capabilities:**

- Hosted booking page per property (or portfolio) with photos, description, amenities, and availability calendar
- Real-time availability check against the master calendar
- Guest inquiry form or instant booking flow (configurable per property)
- Basic custom domain or subdomain support (e.g., `bookings.client-domain.com`)
- Automated confirmation email on direct booking
- **End-to-end direct booking payment flow:**
  - Guest selects dates → real-time price breakdown displayed (nightly rate × nights + cleaning fee + taxes) → guest enters details → pays via Stripe → instant booking confirmation email
  - Support for split payments: deposit at booking (configurable percentage or flat amount), remaining balance auto-charged on a configurable schedule (e.g., 30 days before arrival)
  - Stripe payment retry logic to prevent double-charging on transient failures
  - Security deposit pre-authorisation via Stripe at booking, auto-released after checkout if no claim
  - PCI compliance handled entirely via Stripe's hosted payment elements (no raw card data touches our servers)

---

## 12. Search, Filtering & Navigation

At scale (50+ properties, hundreds of bookings), calendar-only navigation is insufficient. Staff need fast search and filtering across all entities.

**Core capabilities:**

- Global search bar: search across properties (by name, address, or tag), bookings (by confirmation code, guest name, or date range), and guests (by name, email, or phone)
- Booking list view with filters: status, property, date range, channel source, payment status
- Property list view with filters: location, owner, property type, status (active/archived), tag
- Guest list view with filters: flag (VIP, do-not-rebook), booking count, last stay date
- Multi-calendar view with filters: by property group, owner, location, or cleaning status
- Saved filter presets: staff can save and name frequently used filter combinations
- Sort by any column in list views (date, amount, name, status, etc.)

---

## 13. Audit Trail & Activity Log

For a multi-user, multi-role platform managing financial data, a complete audit trail is essential for compliance, dispute resolution, and operational accountability.

**Core capabilities:**

- Immutable activity log recording all significant actions: who did what, when, and from where (IP address)
- Tracked actions include: booking creation/modification/cancellation, rate changes, payment recording/refund, property edits, user role changes, calendar blocks, owner statement generation, guest profile changes
- Log entries include: timestamp, user ID, user role, action type, entity affected, before/after values for changes
- Filterable log view for admins: filter by user, action type, entity, and date range
- Retention policy: audit logs retained for a minimum of 7 years (financial compliance)
- Audit logs are read-only — no user (including Super Admin) can modify or delete log entries

---

## 14. Data Import & Onboarding

New clients migrating from spreadsheets, another PMS, or manual processes need a clear path to get their data into the platform. Poor onboarding is the #1 reason for PMS churn.

**Core capabilities:**

- CSV import for properties: bulk upload of property details (name, address, type, capacity, amenities) via a standardised template
- CSV import for existing bookings: future reservations with guest details, dates, amounts, and status
- iCal import to seed availability from existing OTA calendars during initial setup
- Import validation: preview imported data, highlight errors and duplicates, allow correction before committing
- Guided onboarding wizard: step-by-step flow for new accounts — create first property, connect channels, configure rates, invite team members
- Sample/demo data mode: pre-populated demo account so new users can explore the platform before entering real data
- Onboarding progress tracker: checklist showing setup completion status (properties added, channels connected, rates configured, team invited, first booking created)

---

## 15. Notifications & Alerts

**Core capabilities:**

- Email notifications for: new booking, booking modification, cancellation, new guest message, cleaning task assigned, maintenance issue assigned
- In-app notification centre for key events
- Configurable notification preferences per user role
- SMS notifications for critical alerts (optional, configurable): double-booking detection, payment failure, urgent maintenance issue
- Notification delivery log: track which notifications were sent, to whom, and delivery status (sent, failed, bounced)

---

## Technical & Non-Functional Requirements (MVP)

- **Backend:** Java (Spring Boot) REST API
- **Authentication:** JWT-based, with OAuth2 social login (Google) as a bonus
- **Database:** PostgreSQL (relational data) + S3-compatible object storage for media
- **Architecture:** Multi-tenant SaaS — each agency/account is isolated at the data layer
- **API-first design:** All functionality exposed via documented REST API (enables future mobile apps and integrations)
- **Security:** OWASP Top 10 compliance, encrypted data at rest and in transit (TLS 1.2+)
- **Uptime target:** 99.5% for MVP with public status page showing real-time system health
- **Backup & disaster recovery:** automated daily database backups with 30-day retention, point-in-time recovery capability, RPO < 1 hour, RTO < 4 hours, backups stored in a geographically separate region
- **API rate limiting:** enforce per-tenant rate limits to prevent abuse and ensure fair usage across accounts
- **Webhook support:** outbound webhooks for key events (booking created, booking modified, payment received, checkout completed) to enable client-side automation and third-party integrations
- **Responsive web app:** works on desktop and tablet; mobile-friendly for housekeeper task views
- **GDPR compliance:** data deletion workflow, privacy policy, cookie consent
- **Logging & monitoring:** structured application logging, centralised log aggregation, uptime monitoring with automated alerting on errors/latency spikes
- **Performance targets:** API response time < 500ms (p95) for core operations, calendar sync latency < 30 seconds for API-connected channels

---

## Out of Scope for MVP

The following are explicitly deferred to post-MVP iterations:

- Native iOS/Android mobile apps
- AI-powered dynamic pricing (native — third-party integration hooks are in MVP)
- Automated guest messaging with AI (AI-drafted replies, auto-response)
- Smart home device integration (smart locks, thermostats, noise monitoring)
- Advanced revenue management and forecasting
- Multi-currency and multi-language support
- Contractor/vendor portal
- Upsells and extras management
- White-label / reseller capabilities
- Advanced review management automation
- Trust accounting (separate owner fund accounts, automated payouts)
- Guest screening and ID verification (Autohost, Superhog integrations)
- Automated tax filing and remittance to authorities
- Drag-and-drop report builder
- Buy-now-pay-later payment options (Klarna, Afterpay)

---

## MVP Success Criteria

The MVP is considered successful when:

1. At least one paying agency account is live and managing 10+ properties through the platform
2. Channel sync (Airbnb + Booking.com) is operating with zero double-bookings for 30 consecutive days
3. Housekeepers are using the task view to complete and log cleaning jobs
4. At least one owner is logging in to check their statements monthly
5. At least 20% of bookings for a pilot client are coming through the direct booking page

---


