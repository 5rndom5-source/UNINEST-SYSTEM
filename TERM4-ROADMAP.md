# UniNest Term 4 Roadmap
Research baseline: 9 October 2026. Provisional presentation: around 23 October; exact date/rubric pending.
Target release candidate: 22 October. Dates are proposed work sequencing, not scheduled background execution.

## Product promise
Find a suitable student room, understand its real costs and location, arrange a viewing, apply and track the outcome without losing progress. Landlords manage inventory and applicants; admins operate a trustworthy marketplace.
No promises of zero defects, a guaranteed distinction, legal certification or beating all competitors. Every feature needs evidence.

## Release gates
Presentation: rehearsed end-to-end journey using synthetic accounts, persisted data, actual email to a verified test recipient, clearly labelled sandbox payments and failure cases. Prepare a backup recording.
Public launch: additionally requires cross-role security testing, payment verification, operational ownership, privacy review, monitored HTTPS hosting, restore-tested backups, abuse controls and an independent security review.
Current implementation is not public-launch approved. Existing Java/JS/static checks are not proof of full browser functionality.

## Baseline
Existing code has role-aware sessions, in-portal support, categorized photos, recovery tokens, demo payments and container files. Actual email delivery, complete authorization coverage, concurrent operations and restore remain outstanding.
Admin IDs 5 and 12 were approved with user authorization on 5 October; mutable display names are not identity keys.
First 9 October fix: capacity was counting all reservations overlapping a requested stay, not peak simultaneous occupancy. Correct the algorithm and add date-boundary tests.

## P0: Reliability and trust
### First delivery: 9 October
Implemented peak-occupancy booking validation, preserving transaction locks. Build passed with 60 Java checks (9 upgrade, 40 sign-out/routing, 11 capacity) and 12 MySQL checks in a new disposable database. These are not browser/concurrency tests. No real booking records were used as fixtures. Local preview started on port 8091. All remaining roadmap items retain their planned or previously documented status.

- Authentication: verified-email onboarding; generic errors; throttling on ALL login routes; session rotation; secure cookies; password-manager support; admin MFA before public launch; reauthentication for email/password changes.
- Authorization: test every record/file endpoint as owner, another user, another role and anonymous. Changing a URL ID must not reveal another person's data.
- CSRF tokens on state-changing forms/fetch calls. Fetch Metadata alone is incomplete.
- Booking: validate dates, active/approved listings and inventory; count simultaneous stays; transactionally reject competing approvals. Explicit confirm/cancel/complete transitions and repeat-click protection.
- Tenancy: specify when booking becomes tenancy; keep cancellation, occupancy and completion consistent.
- Money: due/pending/paid/failed/cancelled/refunded are distinct. Never trust browser success URLs. Verified provider events, unique event IDs, replay protection, receipts and reconciliation required for real payments.
- Recovery: received email, expired/reused/invalid token, delivery failure, rate limit and old-session rejection.
- Uploads: content validation, ownership, limits, atomic replacement, orphan cleanup and private document access.
- Versioned additive migrations; backup and proven restore. Never apply the destructive fresh schema to user data.

## P1: Accommodation requirements
- Model building/property, room type, physical room/bed inventory and dated availability separately. Preserve legacy room IDs in an explicit migration.
- Single/shared/double/studio/apartment; private/shared kitchen/bathroom; occupancy, furnishing and accessibility.
- Galleries scoped to the correct property/room type: exterior, rooms, kitchen, bathroom, shower/tub/toilet and communal areas. Add cover selection, captions, reorder/delete, progress and failure handling.
- Transparent monthly rent, rent period, deposit, fees, included utilities, usage limits and move-in total. Missing data must not become invented zero costs.
- Minimum stay, move-in dates, house rules, cancellation/refund terms and eligibility. Verification/accreditation badges require evidence and expiry.
- Search by campus/suburb, budget, dates, room type, utilities and accessibility; useful sorting, map/list views, clear filters, shareable URLs and honest empty states.
- Compare up to three properties using consistent fields. Saved listings/searches tied to account IDs.
- Viewing slots with conflict checks, cancellation, timezone, confirmation and calendar export.
- Application timeline, required documents, next action and reasons; reuse profile data with consent.
- Maintenance categories, urgency, photos, assignment, progress and resolution evidence.
- Reviews restricted to eligible stays; reporting/moderation; suspicious-listing reports and abuse handling.
- Landlord drafts, preview/publish, occupancy calendar, application queue and bulk availability.
- Admin verification queue, support assignment, per-admin unread status, moderation history, audit trail and scoped privileges.

## Low-effort interaction
Preserve filters and return paths through login/support/Back. Use progressive forms, inline validation, focus on errors and duplicate-submit prevention. Recover non-sensitive drafts without storing passwords, ID documents or financial details in localStorage.
Provide distinct loading/empty/error/offline states with retry. Notifications link to the relevant record, with preferences and digest options.
Test keyboard navigation, focus, labels, screen-reader status, contrast, touch targets, narrow phones and slow networks. Allow password paste/autofill. Compress/lazy-load photos and paginate server-side.
Do not add a chatbot in place of reliable core workflows. Guided recommendations can follow trustworthy inventory.

## Maps and campus access
1. Structured country/city/suburb/address; verified coordinates or provider Place ID; location precision and disclosure controls. Never invent exact pins from suburb-only data.
2. Address autocomplete with manual fallback and landlord pin confirmation; map/list search and campus markers.
3. Walking/driving/transit directions where supported; nearby amenities; optional Street View. Label estimates. Straight-line distance is not travel time and a suggested route is not a safety guarantee.
Google Maps URLs support external search/directions without an API key. This is a fallback, not an embedded-map/autocomplete integration.
Compare Google Maps Platform and Mapbox on South African coverage, storage rights, quotas and total cost. Tiles, geocoding and routing are separate capabilities. Restrict browser keys, protect server secrets, debounce requests and cache only as permitted.
Do not treat public OpenStreetMap community services as unlimited production hosting. Ask for geolocation permission and provide campus/address entry. Missing keys and provider outages must leave listings usable.

## Two-week sequence
| Dates | Work | Exit evidence |
| --- | --- | --- |
| 9-10 Oct | Baseline, risk register, booking correctness and repeatable tests | Reproduced defect; regression protection |
| 11-12 Oct | Authentication, authorization, email and CSRF | Cross-role denial checks; inbox reset proof |
| 13-14 Oct | Room inventory, gallery controls and transparent pricing | Landlord-to-student listing journey |
| 15-16 Oct | Location model, map fallback/provider integration, search/compare | Verified addresses; outage fallback; mobile checks |
| 17-18 Oct | Viewing/application/tenancy journey and sandbox checkout | Success, cancellation, duplicate and failure evidence |
| 19-20 Oct | Support, maintenance, accessibility and staging | Two-account messaging; HTTPS; persisted uploads |
| 21 Oct | End-to-end regression, concurrency and restore | Test matrix; restored data/files; triaged defects |
| 22 Oct | Feature freeze and rehearsal | Seeded demo, script, architecture notes, backup video |
| After presentation | Public-launch hardening and independent review | Launch gates satisfied |

If time is insufficient, defer advanced commute estimates, comparison, calendar sync and recommendations. Do not sacrifice authorization, booking integrity or data recovery for visual extras.

## Integration and deployment decisions
### Confirmed first-month budget: USD 30
User confirmed USD 30 for one month. Planning envelope, not supplier quotes: USD 15 for app/database/storage; up to USD 10 for a sender domain if one is not already owned; USD 5 reserve for tax/FX/usage variation. If a domain exceeds that allowance, use an existing domain or revise the allocation before buying. Domain registration is usually paid annually, not USD 10 every month.
Use the hosting subdomain for the site initially. Resend Free currently allows 3,000 emails/month with a 100/day limit; sender-domain verification is still required for normal external recipients. Use no paid map API initially: keyless map links are the fallback, not a substitute for the planned embedded integration.
Railway usage must be measured with this Java/MySQL workload before treating the USD 15 allowance as feasible. Set alerts and an appropriate service limit before deployment; a protective shutdown can interrupt the presentation. Keep an offline demo backup. Do not provision paid resources until actual prices, domain ownership, tax and total projected cost fit USD 30.
Sources checked 9 October: https://resend.com/pricing and https://docs.railway.com/pricing/plans

Keep Java/Tomcat/MySQL for this release; no deadline-driven framework rewrite.
Email: configure existing Resend adapter with verified domain/DNS, then test delivery. Add outbox/retry and bounce handling before relying on notifications.
Payments: evaluate Payfast sandbox; live merchant approval, fees, notification verification, refunds and reconciliation are separate work.
Hosting: Railway is a convenience-first candidate for Docker/MySQL/persistent uploads. Hobby is USD 5/month minimum with USD 5 included usage, NOT a guaranteed final bill. App/database resources, storage, traffic, domain, maps and email can increase cost. Budget and approval required before paid provisioning.
Persist uploads separately from the deployment filesystem. Evaluate object storage/private signed document access when scaling.
CI: build, Java/JS checks, disposable MySQL tests, secret/dependency scans and staging smoke tests. Add health/readiness, redacted logs, request IDs, alerts, cost limits and rollback.
Privacy: inventory collected data, define retention/deletion, notices, access/export requests and breach response; obtain appropriate South African POPIA review. This is not legal-compliance certification.

## Definition of done
### Security increment verified 12 October 2026
Session-bound CSRF tokens now protect unsafe HTTP methods. Shared browser handling covers the 41 static HTML pages; generated reset-password and payment-simulation forms include server-issued tokens. GET logout displays confirmation and preserves the session; token-protected POST ends it and returns the role chooser. No account or production data changes were needed for these checks.

Verification: 94 Java regression checks, 16 live sign-out checks, 11 live login-throttle attempts, and 12 isolated Edge browser/HTTP CSRF checks passed. Browser checks include native multipart submission, fetch uploads reaching authentication, wrong-session and URL-token rejection, and logout navigation. These anonymous checks do not replace authenticated end-to-end testing of every portal. Email delivery still awaits sender configuration; deployment and paid services remain unprovisioned.

Requirement plus failure scenarios; reviewed migration; server validation/authorization; success/error/retry/duplicate states; passing automated checks; recorded real-browser journey; mobile and keyboard verification; secret-safe logs; timeout/retry limits; documentation and rollback.
Statuses must distinguish planned, implemented, automated-tested, browser-tested and blocked on external configuration.

## Presentation and CV evidence
Demonstrate: campus search -> room-specific gallery/cost comparison -> viewing/application -> landlord approval without overbooking -> labelled sandbox payment/receipt -> support reply. Show recovery and cross-account access denial.
Deliver actual-schema ERD, architecture, requirements-to-tests matrix, test evidence, deployment runbook and reproducible synthetic demo data. CV claims must distinguish implemented work from planned APIs.

## Sources, accessed 9 October 2026
- OWASP ASVS 5 authentication: https://github.com/OWASP/ASVS/blob/master/5.0/en/0x15-V6-Authentication.md
- OWASP password recovery: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
- W3C accessible authentication: https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html
- Student.com listing example (bills and booking details, not an exhaustive competitor study): https://www.student.com/ca/toronto/p/mutual-st?force_v1=1
- Google Maps URLs: https://developers.google.com/maps/documentation/urls/get-started
- Mapbox product pricing: https://www.mapbox.com/pricing
- Resend domain verification: https://resend.com/docs/dashboard/domains/introduction
- Payfast developer portal (candidate, not implemented): https://developers.payfast.co.za/docs
- Railway plans: https://docs.railway.com/pricing/plans
- Information Regulator privacy starting point: https://inforegulator.org.za/popia/
Commercial terms must be checked again before purchase. Provider selection and compliance require further validation.
