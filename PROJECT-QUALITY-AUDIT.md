# UniNest quality audit

Reviewed: 27 October 2026. Scope: first researched improvement batch, not a production certification or exhaustive penetration test.

## Implemented in this batch

| Files | Changes |
| --- | --- |
| experience.css, experience.js | Shared focus indicators, skip navigation, keyboard profile menus, restrained motion with reduced-motion support; responsive search layout and collapsible mobile filters. |
| csrf.js | Loads shared experience assets without changing token handling. |
| HomePage/public-listings.js | Cancels obsolete searches, ignores stale responses, persists filters in the URL, validates price ranges, bounds pagination, adds retry and busy states, lazy-loads images, opens public listing galleries without forcing login. |
| HomePage/search.html | Visible search/price labels, semantic filter disclosure, clearer page heading and accessible filter names. |
| HomePage/home.html, about.html, contact.html, search.html | Replaces nonfunctional newsletter forms and subscription promises with working account, saved-listing and contact links. |
| system-status.js | Correct context-relative endpoint, timeout, no overlapping polling, pauses while hidden and refreshes on return/connection. |
| Admin/src/java/com/uninest/servlets/system/DatabaseStatusServlet.java | Returns HTTP 503 when database connectivity fails instead of a misleading success. |
| Admin/src/java/com/uninest/servlets/system/SessionConfiguration.java | Cookie-only session tracking and HttpOnly; Secure when UNINEST_PUBLIC_URL uses HTTPS. |
| META-INF/context.xml | Explicit SameSite=Lax session cookies. |
| pom.xml, Dockerfile | Packages shared experience assets. |
| tools/check-experience.cjs | Repeatable browser regression coverage for search, keyboard controls, responsive layouts and reduced motion. |

## Verification

- Maven WAR build successful; 94 Java regression checks passed (upgrade, sign-out, booking capacity, login throttling and CSRF).
- 24 experience browser checks passed, including 320/390/1280px layouts and stale-search handling. These use mocked API responses, not production services.
- Static page audit covered 41 HTML pages with no issues reported by that tool; this is not proof that every business workflow is correct.
- Mobile and desktop search screenshots inspected.
- Real HTTPS cookie delivery, email delivery, Docker startup and cloud-volume permissions still require deployment-environment verification.

## Second batch: message delivery integrity

- StudentMessageServlet, ContactLandlordServlet and SendMessageServlet now use MessageDelivery to commit message records, attachment references and notifications together, rolling back on failures or early returns.
- MessageActionServlet uses MessagePermissions to allow read acknowledgements only from the recipient or the shared administrator inbox. An administrator cannot acknowledge their own outgoing reply on behalf of its student/landlord recipient.
- MessageAttachmentSupport removes partially copied files when copying fails; MessageDelivery removes saved attachments after a successful rollback.
- MessageDeliveryChecks covers permissions, success, early return, notification errors, commit errors, rollback errors and nested-transaction rejection. Database fault-injection and real provider delivery remain separate follow-up checks.
- Research: [OWASP authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) and [JDBC transactions](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html).
- Verification: Maven build passed, all 106 Java checks passed (including 12 new messaging checks), and 30 support-navigation checks passed. Transaction tests use simulated JDBC connections; live database fault injection is not yet verified.

## Priority 0: before public deployment

### Backup and restore batch: 1 November 2026

- Added tools/backup-uninest.ps1: database dump, upload copies, SHA-256 manifest and table row counts, with an explicit paused-writes prerequisite. Backups stay in ignored test-runtime/backups.
- Added tools/test-backup-restore.ps1: checksum validation, fresh disposable database import, per-table row-count comparison and separate upload restoration with hash checks. No production restore target is accepted as a parameter.
- Added BACKUP-RECOVERY.md with operation, privacy, migration and recovery limits.
- Passed a real restore of the disposable funding database: 26 tables and one synthetic file. No production records were changed. Failed/successful test databases are retained, not automatically deleted.
- Still outstanding: encrypted off-device backups, retention/alerts, cloud-provider testing and application-level validation against restored data. No automatic schedule was enabled.

### Attachment hardening: 28 October 2026

- MessageAttachmentSupport now checks JPG/PNG/WebP/PDF signatures and limits actual stream bytes to 5 MB instead of trusting only the supplied metadata. Signatures do not establish full document validity or malware safety.
- Attachment names use full UUIDs and strip browser-supplied paths. Invalid/path-traversing stored names are rejected. Partial writes are cleaned up without deleting a pre-existing file on a name collision.
- MessageAttachmentServlet rejects symbolic-link files and forces downloads with sanitized filenames rather than rendering untrusted attachments inline. Existing account/recipient authorization remains in place.
- Message attachments now use UNINEST_UPLOAD_DIR/message-attachments, consistent with profile and listing storage. The default remains user.home/.uninest/message-attachments. Before setting or changing UNINEST_UPLOAD_DIR, copy existing profile-images, property-images and message-attachments directories into the new root, preserve filenames, set service-user permissions and verify downloads. No existing files are automatically moved.
- AttachmentChecks uses isolated synthetic files for MIME spoofing, dishonest size metadata, traversal, configured storage, filename handling and cleanup. Cloud restart persistence and malware scanning remain deployment work.

### Booking and funding batch: 28 October 2026

- StudentBookingServlet: booking creation and its notifications now run in one transaction. A room lock serializes concurrent requests, and a locking duplicate lookup rejects the second active booking. Unknown actions return 400 instead of creating a booking. Cancellation validates reason length and uses the shared lifecycle service.
- BookingTransitions: confirmation checks listing approval/availability and creates a tenancy once. Cancellation terminates active tenancy records; completion marks them completed. Student cancellation verifies ownership and notifies the landlord. Paid receipts are retained; cancelling is not a refund.
- Kept the duplicate-booking fix in the existing servlet rather than introducing another helper; lifecycle reuse is through BookingTransitions.cancelByStudent.
- DemoFundingChecks: expanded real MySQL coverage for tenancy consistency, ownership, terminal bookings, retained payments and a deliberately failing database trigger to prove rollback.
- start-funding-test-preview.ps1 and check-funding-browser.cjs: seed properties rather than accepted bookings; exercise simultaneous student requests, landlord approval, payment decline/retry, NSFAS split receipts and cancellation through real HTTP/browser interactions.
- Verification: 106 Java checks and 82 MySQL checks passed. The live two-account browser journey passed on desktop and mobile. Browser plugin not available; existing Playwright/Edge runner used.
- Fully funded bursary coverage and simultaneous provider receipts passed in the MySQL suite; the browser suite covers self-funded and partial NSFAS scenarios.
- Demo preview is isolated at http://localhost:8092/uninest/ using a disposable database. Local test accounts only: student@example.invalid / Changed!2026 (student), landlord@example.invalid / Testing!2026 (landlord). These are fixture credentials, not real accounts.
- Normal preview does not enable demo payments. No real money, NSFAS integration, email-provider delivery, automatic refunds or recurring monthly invoices were introduced. Existing application bookings were not migrated or modified by these tests; old confirmed bookings without tenancies are not automatically backfilled.

1. Restrict administrator activation to an authorized administrator. Public signup must never automatically grant active administrator privileges. Verify permissions server-side on every privileged endpoint, including ownership checks for uploads and bookings.
2. Configure a verified email domain and provider secret outside Git. Test recovery delivery, expired/reused tokens, rate limits and neutral responses for unknown accounts.
3. Configure HTTPS and the canonical UNINEST_PUBLIC_URL. Verify Secure, HttpOnly and SameSite cookies behind the selected reverse proxy, and confirm logout invalidates sessions.
4. Store uploaded photos on durable storage with correct container-user permissions. Test persistence after restart/redeploy and reject invalid content, oversized files and unauthorized reads/writes.
5. Back up the database and uploaded files, then perform an actual restore into an isolated environment. Set retention and monitor failures.
6. Apply database migrations with a backup and rollback plan. Funding simulation migration/activation must be verified separately; do not label simulated payments as real transactions or imply an official NSFAS integration.
7. Test the complete deposit-plus-rent workflow, bursary-funded handling, cancellation, duplicate submission, capacity contention and authorization in an isolated database.
8. Scan dependencies, container image and Git history for vulnerabilities/secrets. Set spending alerts, log retention and uptime checks. A hosting provider alone does not guarantee security.

## Priority 1: presentation and product quality

- Run a role-by-role journey matrix: registration, approval, login/logout, profile photo, support, listing draft/edit/photos, booking, cancellation, funded booking and payment simulation.
- Verify support messaging works for approved support administrators without depending on display names. Preserve the current portal and login state when contacting support.
- Improve trust signals: listing verification state, report-listing action, moderation history and accurate availability. Never display unverified badges or invented ratings.
- Clarify rent period, deposit, included utilities, additional fees and funding status before booking. Define cancellation/refund rules and obtain appropriate review of rental/privacy terms.
- Test every form and modal with keyboard navigation, screen readers, zoom, validation errors and narrow screens. Shared focus improvements do not establish WCAG conformance.
- Maps: validate addresses, restrict API keys by origin/API, set quota alerts, provide directions links and handle unavailable Street View gracefully. Do not promise 3D imagery for every property.
- Add accessible empty, loading, retry and success states consistently across remaining dashboards. Avoid animation that delays work or obscures content.

## Priority 2: operational maturity

- Administrator MFA, audit logs for privileged changes, abuse reporting, monitoring and incident response.
- Connection pooling and load tests; review rate limiting before running multiple instances.
- Image delivery/cache tuning, retention/deletion policy and export/delete-account processes.
- Automated CI with Java and browser checks and a staging environment before production releases.

## Research basis

Recommendations are adapted to the existing Java/Tomcat/MySQL application, not a framework rewrite.

- WCAG 2.2: https://www.w3.org/TR/WCAG22/
- Focus visibility: https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum
- Visible form labels: https://www.w3.org/WAI/tutorials/forms/labels/
- Accessible motion: https://web.dev/learn/accessibility/motion
- OWASP session management: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- OWASP upload safety: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- Tomcat cookie configuration: https://tomcat.apache.org/tomcat-9.0-doc/config/cookie-processor.html

No GitHub push or external deployment was performed for this batch. Existing database records were not intentionally altered by the mocked experience tests.
