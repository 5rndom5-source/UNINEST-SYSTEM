# UniNest final-term review

Date: 2026-09-27. Scope: targeted static review of Java servlets, Maven configuration, shared browser code, SQL schema and available historical logs. No application code or database records changed. No live end-to-end tests performed. This is not an exhaustive security audit. The University directory was inaccessible and excluded.

## Priority findings

### P1: Pending admins can log in, but cannot receive support messages

- Admin/src/java/com/uninest/servlets/auth/LoginServlet.java:66 rejects suspended and inactive accounts, but does not require active status or a matching admins row before creating an admin session.
- UserDashboard/java-servlets/com/uninest/servlets/student/StudentMessageServlet.java:169 and Landlord/java-servlets/com/uninest/servlets/landlord/ContactLandlordServlet.java:164 require an active admin with an admins row, then select the lowest user ID.
- Thus successful admin login does not prove support is configured. The reported error is returned when that recipient query finds no account. Live records were not checked, so the specific missing condition is unconfirmed.
- Fix: require approved/active admin identity consistently; provision an approved support account through a controlled setup process; explicitly configure support routing and show its health in administration. Do not automatically approve every admin.
- Verify: pending admin cannot log in; approved admin receives student and landlord messages; replies appear in the correct conversation; missing support produces a useful setup error.

### P1: Booking confirmations allow conflicting and invalid state changes

- Admin/src/java/com/uninest/servlets/bookings/BookingActionServlet.java updates by booking ID without checking the previous status or overlapping reservations.
- Landlord/java-servlets/com/uninest/servlets/landlord/BookingDecisionServlet.java checks ownership, but likewise lacks prior-status and room/date-capacity checks.
- StudentBookingServlet checks duplicates only for the same student and room; this does not protect against conflicting confirmations for different students.
- Fix: centralize allowed transitions, date overlap and room capacity validation; lock the relevant room/booking rows inside a transaction. Confirmation must create the tenancy and initial invoice once. Define cancellation and completion effects.
- Verify simultaneous confirmations, cancelled-to-confirmed attempts, overlapping dates, capacity greater than one and repeat submissions.

### P1: Payment status and accounting history can disagree

- Admin/src/java/com/uninest/servlets/payments/PaymentsServlet.java:61 updates payment status/date/receipt, then separately inserts transaction history and notifications, without a transaction.
- An error after the update leaves a partially completed operation. A completed payment can be changed back to pending/failed while its credit remains. Repeating completion rewrites the date and can change the receipt without updating the existing credit.
- Fix: explicit state transitions, transaction boundaries, idempotency and an auditable reversal/refund flow. Do not treat client-submitted success as payment-provider confirmation.
- No INSERT INTO payments or INSERT INTO tenancy_records was found in the searched application Java sources. The present UI updates existing payment records rather than providing a complete invoice/payment lifecycle.

### P1: Database connection is shared across requests

- Admin/src/java/com/uninest/utils/DatabaseConnection.java stores one Connection in a singleton. Synchronizing the getter does not protect callers while they execute queries.
- Concurrent requests share connection state; adding transactions on this connection would allow one request to affect another. Closed checks alone do not validate a stale network connection.
- Fix before transaction work: pooled DataSource, one borrowed connection per operation, try-with-resources, rollback on failure and connection validation.

### P1 before public hosting: Password hashing needs strengthening

- Admin/src/java/com/uninest/utils/PasswordUtil.java performs a single salted SHA-256 digest. This is too fast for password storage if the database is stolen.
- Replace with a maintained password-hashing implementation such as Argon2id or bcrypt; retain legacy verification temporarily and rehash after successful login. Review rate limiting, CSRF protection and secure session-cookie settings before exposing the app publicly.
- No csrf matches or transaction calls were found in the searched Java sources; runtime/container protections were not verified.

### P2: Forgot-password is a placeholder

- Admin/src/java/com/uninest/servlets/auth/ForgotPasswordServlet.java:34-48 looks up a user and inserts an in-app notification. It does not create a reset token, send an email or provide a password-reset completion flow.
- Preserve the generic response to avoid revealing registered addresses, but implement the actual flow described below.

### P2: Profile-photo storage and replacement need hardening

- Admin/src/java/com/uninest/servlets/account/ProfileImageServlet.java:55 deletes all existing image variants before writing the replacement. A failed write can remove the old photo.
- The file is trusted based on the submitted MIME type rather than decoded as an image. A non-image with an allowed MIME label can be accepted and then fail to display.
- Storage is fixed to user.home/.uninest/profile-images. On a cloud deployment it needs a persistent volume or object storage; the server account needs write permission.
- Fix: validate/decode images, write a temporary file and atomically replace after success, return useful JSON for size/storage errors and make storage location configurable.
- The admin upload form and servlet both use the photo field, so there is no confirmed field-name mismatch. The reported upload failure itself remains unconfirmed: reproduce with a small valid PNG and inspect the HTTP response/server log.

### P2: Root deployment breaks shared API paths

- portal-identity.js:4 assumes the first URL segment is the application context. At /uninest/Admin/... this works; at /Admin/... on a root deployment it incorrectly calls /Admin/ProfileImageServlet and similar endpoints.
- Keep /uninest consistently for the first deployment, or inject the real application base URL and update all API helpers together. Test both root and subpath deployment.

### P2: Deleting operational records can erase financial history

- Database/Uninest_DB.sql:349-390 cascades deletions through payments and transaction_history; upstream users/bookings/tenancies also cascade.
- Before using real payments, adopt archival/soft-delete rules and preserve accounting records. Backups alone do not replace a retention policy.

## Final-term feature plan

1. Repair account approval/support routing and reproduce profile uploads first.
2. Introduce pooled database access and transactional booking/payment services.
3. Add a clearly labelled simulated checkout: success, failure, cancellation and repeat-click cases; server-calculated amounts; unique references; invoice, payment, ledger and receipt linked together. No real card data or charges. Keep simulation disabled outside demo/test environments.
4. Build password-reset request -> captured email -> single-use expiring link -> password change -> old token rejected. Store a hash of the random token, not the raw token; rate-limit requests; invalidate existing sessions as appropriate. Never email passwords. Use a private test email outbox initially, then an email provider for real delivery.
5. Add focused integration tests for each role, ownership checks, upload failures, messaging, concurrent bookings, duplicate payments and expired/reused reset links. Test against an isolated MySQL database with deterministic demo records.
6. Deploy a staging instance and automate builds/deployment from a private Git repository; run migrations explicitly; configure secrets, persistent uploads, HTTPS, health checks, backups and a tested restore procedure.

## Hosting comparison

The application is a Java 11-targeted WAR using javax.servlet 4 and MySQL. Use a compatible Tomcat 9 container initially; a newer Jakarta-only container is not a drop-in replacement. Keep the database and uploads persistent.

### Railway: recommended for less manual deployment

- Hobby is a $5/month minimum including $5 resource usage; excess usage is billed separately. This is not a guaranteed $5 total for Java + MySQL + storage.
- Docker/GitHub deployment and a MySQL service fit the current stack. Proposed setup: Tomcat application container, private MySQL service, persistent upload volume, environment variables and health checks.
- Measure resource usage during staging before committing to a budget. A $10-20 monthly planning allowance is an estimate, not a provider quote; taxes, usage and exchange rates can change the total.
- https://railway.com/pricing
- https://docs.railway.com/databases/mysql

### Hetzner: lower server cost, more administration

- Current June 2026 adjustment lists CX23 at EUR 5.49/month excluding VAT in the EU locations; confirm the selected region and checkout total. IPv4, backups and other extras are additional as applicable.
- Can host application and MySQL on one small VPS, but we own OS patching, firewall/TLS setup, backups, monitoring and recovery. It is less suitable when the main goal is reducing manual maintenance.
- https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/

### Render: viable but less natural for this MySQL project

- Starter web compute is listed at $7/month; this is not the total for a database and persistent storage. MySQL requires a custom database service with persistent disk rather than the native managed PostgreSQL path.
- Free web services cannot retain local uploads on a persistent disk. Do not select free hosting without redesigning file persistence.
- https://render.com/pricing
- https://render.com/docs/faq
- https://render.com/docs/free

Recommendation: use Railway staging for the final-term demonstration, with a measured spending budget, after the P1 findings are repaired. No cloud account, purchase or deployment was performed during this review.
