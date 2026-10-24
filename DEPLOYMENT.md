# UniNest upgrade and deployment guide

## Status (5 October 2026)

Implemented: validated JPEG/PNG profile uploads with atomic replacement; configurable image storage; categorized multiple property images and public/student/landlord galleries; password recovery through the Resend HTTPS API; hashed, single-use reset tokens with 20-minute expiry; session invalidation after reset; PBKDF2 password hashing with legacy verification; request-scoped database connections; booking transition/capacity locking; transactional admin payment updates; opt-in demo checkout; container packaging.

This is not production sign-off. The configured MySQL credentials worked. A backup was saved under test-runtime/backups and the additive migration was applied successfully to the existing database. Nine regression checks and nine MySQL integration checks passed; the latter used a separate disposable database. Backup restoration, authenticated browser workflows, real email delivery and concurrent booking/payment behaviour remain unverified. No external email was sent, no cloud subscription was purchased and nothing was deployed externally.

Support already has a shared admin inbox and notifications for active admins. Admin account IDs 5 and 12 were explicitly approved by the user on 5 October 2026 and then set active. Approval grants access to private conversations and administrative controls; it is tied to account IDs, not display names.

## Database migration

1. Back up the existing MySQL database and confirm the backup can be restored.
2. Apply `Database/migrations/001-final-term.sql` ONCE to the existing database. It adds image categories, password-change timestamps and the reset-token table. Do not rerun it blindly: the ALTER statements are not repeatable.
3. Do NOT run `Database/Uninest_DB.sql` against existing data: it drops and recreates tables. That file is only for a fresh disposable database.
4. Configure `UNINEST_DB_URL`, `UNINEST_DB_USER` and `UNINEST_DB_PASSWORD` for a dedicated application account. The account needs application table access, not global database administration.
5. Ensure at least one approved support account has `users.role='admin'`, `users.status='active'`, and a matching `admins.user_id`. Pending admins can no longer use either admin login route. Do not bulk-activate unreviewed accounts.

The new session validation requires the migration. Deploying the WAR before migration will cause authenticated requests to fail.

## Login abuse protection (9 October update)

All three login POST endpoints share an in-memory limiter: 10 attempts per normalized email and 60 per connector IP within a 15-minute fixed window. All attempts count, including successful sign-ins; these are temporary request limits, not database account locks. Switching roles/endpoints does not reset the counters. A blocked request returns HTTP 429, Retry-After, a role-chooser link and a recovery link without echoing the email.

The limiter is bounded to 10,000 hashed keys and does not evict active limits to accept new keys. A full table rejects new buckets temporarily. Tests cover expiry, normalization, IP/account limits, concurrent attempts, saturation and alternate login routes.

Current scope is one application instance. Counters reset on restart. Before horizontal scaling, use a shared limiter at the gateway or shared store. Do not trust arbitrary X-Forwarded-For headers: configure and test Tomcat's trusted proxy handling for the chosen host, otherwise users behind a proxy share its IP allowance. Distributed abuse, account-targeted temporary denial, credential-stuffing monitoring and timing-based enumeration still require broader hardening.

The dedicated admin endpoint no longer discloses inactive status before verifying credentials. Inactive-account failures use generic credential errors on both general/admin routes.

## Actual password-recovery email

The current delivery adapter uses Resend. A different provider can be added after the subscription is chosen.

- Verify a sender domain in the provider dashboard and add its required DNS records.
- Set `UNINEST_RESEND_API_KEY` through the host's secret settings, never in source control or chat.
- Set `UNINEST_EMAIL_FROM`, for example `UniNest <noreply@your-domain.example>`.
- Set `UNINEST_PUBLIC_URL` to the external HTTPS application URL INCLUDING `/uninest`.
- Open `/uninest/ResetPasswordServlet`, request a link for a test account, verify inbox/spam delivery, reset the password, sign in, then confirm the old link and previous sessions fail.
- Requests are limited per account and per remote address. Behind a proxy, the remote-address limiter can be shared by users until trusted proxy addressing is configured.
- Provider errors are logged without API keys or reset tokens. Provider acceptance does not prove inbox delivery; complete the inbox test before claiming recovery works.
- Access logs should exclude query strings on the reset endpoint to avoid recording tokens.

## Property and profile images

- Set `UNINEST_UPLOAD_DIR` to a writable persistent directory. With Docker use `/data/uploads` and mount a persistent volume there, writable by UID 10001.
- Migrate existing `.uninest/profile-images` and `.uninest/property-images` folders into this root when moving servers. Back up both the database and files.
- New uploads accept JPEG/PNG, up to 5 MB and 20 megapixels each. Images are decoded and re-encoded; previous WebP files can still be served, but new WebP uploads are not supported by the bundled Java decoder.
- Landlord sections: building, single room, double room, kitchen, bathroom, shower, tub, toilet and other. Up to 20 images/45 MB per submission. New edits append to the gallery without resetting the cover flag on every upload.
- Gallery deletion, cover selection and reordering are still follow-up work. Listing-image file cleanup after database commit failures/deletion also needs integration testing.

## Payment simulation

- Use an isolated demo database. Set `UNINEST_DEMO_PAYMENTS=true` only for that environment.
- Signed-in students see Demo payments in navigation and can open `/uninest/PaymentSimulationServlet` for confirmed bookings.
- Select successful, failed or cancelled outcomes. Server-side room rent determines the amount; no card details are requested and no actual charge is made.
- Records and receipts have `DEMO-BOOKING-...` references; completed attempts cannot be credited again. Each booking represents one demo rent payment, not recurring billing.
- Demo records use the existing finance tables and therefore contribute to existing totals. Never enable this against a real-money database.
- A real payment provider, verified callbacks, refunds, recurring invoices and reconciliation are NOT implemented.

## Deployment

1. Build from a private Git repository using the supplied Dockerfile (Tomcat 9 / Java 11, compatible with the current javax.servlet code).
2. Keep the application at `/uninest`. Several older page scripts still assume this context; root-context deployment has not been fully migrated.
3. Add a private MySQL service and persistent upload volume. Set variables using `deployment.env.example` as a checklist; do not commit real values.
4. Expose port 8080 through the host's HTTPS endpoint. Configure secure session cookies and trusted proxy headers for that host.
5. Run the migration before deploying the upgraded application. Configure backups and test restore.
6. Connect GitHub deployment to a tested branch. Add a CI job running the build and regression checks before deployment.

Railway is a reasonable convenience-first target; a VPS requires more operational maintenance. The Docker image has not been built locally. Choose the subscription before provider-specific routing, secrets, domain and backup setup.

## Verification

Sign-out follow-up: all portal sign-outs and legacy logout URLs now return to the shared login with no role preselected. Dedicated login pages offer role switching. Static portal HTML requires the correct session on the server; expired sessions return to the shared login and wrong-role visits redirect to the signed-in role's home. Portal responses are not cached, restored browser-history pages reload, and visible tabs recheck the session. These guards supplement, rather than replace, each servlet's authorization checks. Forty dependency-free sign-out/routing checks cover session invalidation, cookie expiry, repeat sign-out, root/subpath deployment, role parameters and anonymous portal access. Complete browser and concurrent-tab end-to-end tests remain required.

- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/check-upgrades.ps1` builds the WAR and runs nine dependency-free regression checks. Maven alone compiles the check class but does not execute its main method.
- After building, `powershell -NoProfile -ExecutionPolicy Bypass -File tools/check-database-upgrades.ps1` creates a separate disposable database and runs nine MySQL checks for booking ownership, transitions, overlapping dates, password reset, token reuse and expiry. It retains the test database for inspection and requires database-creation permissions; do not grant those permissions to the deployed application account.
- `python tools/audit-pages.py` checks local HTML file references and annotated servlet routes. 41 pages passed the static link/asset check; this is not a test of every page's functionality.
- `tools/start-upgrade-preview.ps1` starts an isolated local preview on port 8091 if available. It does not apply migrations or configure email.

Still required before launch: broader database integration tests; cross-role authorization tests; image error handling in the browser; support message/reply testing; simultaneous booking approvals; demo repeat-click/receipt checks; real inbox reset tests; end-to-end mobile/desktop verification; complete CSRF coverage (the current filter rejects cross-site Fetch Metadata requests but is not a full token-based system); login throttling; connection pooling/load tests; payment lifecycle completion; upload retention; backup restore and rollback rehearsal.

## Research used

- https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://resend.com/docs/api-reference/emails/send-email

The earlier FINAL-TERM-REVIEW.md remains the baseline findings. This file records the changes and remaining validation rather than treating that earlier report as a completed checklist.
# CSRF Integration

All unsafe methods require a session-bound token, including anonymous login and registration. Static pages load `csrf.js` before application scripts; it adds tokens to native forms and same-origin fetch writes. Server-rendered forms must include `CsrfToken.field(request)`. GET `/CsrfTokenServlet` returns a no-store token tied to the session cookie. Never put tokens in URLs. External API clients must retain cookies and send `X-CSRF-Token`; no webhook exemptions exist. Future provider callbacks require separate signature verification and a narrowly reviewed exemption.

Logout links use POST; visiting the logout URL directly only shows confirmation. JavaScript is required for token injection into static forms. Browser tests: run `tools/check-csrf-browser.cjs` with Node and Playwright/Edge against the local preview. These tests use anonymous disposable sessions and do not verify real email delivery or authenticated upload success.
# October 23 Security and Registration Update

Pause the Railway deployment recommendation pending review of its October 21 incident: https://blog.railway.com/p/security-disclosure-wednesday-october-21st . The user reports connecting a GitHub repository but adding no secrets. Review/revoke the GitHub integration and inspect account/repository activity. A repository may still contain committed credentials even when no environment variables were entered. No remote permissions or credentials have been changed by this update.

Admin registration now creates a pending-verification user and moderator record, with notifications to active admins in the same transaction. It does not activate accounts or bootstrap the first administrator. The system owner must separately authorise activation; there is not yet an approval-management screen. All teammates need the updated WAR, matching schema, and a reachable shared server if they expect shared accounts; localhost on another PC is a different server.

Landlord listings accept a public full address in the existing location column (255 characters maximum). Existing suburb-only values remain editable. Listing cards provide external Google Maps searches without an API key. Embedded Maps, automatic geocoding, and Street View/3D are not implemented in this increment. Google imagery availability must not be presented as an interior property tour.

## October 24 listing and team update

The October 23 notes above describe that earlier increment. Room-by-room uploads, gallery editing and optional Maps Embed integration are now implemented; see LISTING-PHOTOS-SETUP.md for migration 003 and API-key setup. No Google key has been configured locally.

An active-admin approval screen now completes team registration at `/uninest/AdminAccessServlet`. Admin header photos are exempt from the legacy CSS rule that hid all header images. Profile uploads resize large images and explicitly send CSRF tokens. See TEAM-ADMIN-SETUP.md for the shared-server workflow and file-by-file changes. These changes do not automatically approve existing pending accounts.
