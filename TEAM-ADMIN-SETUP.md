# Team admin accounts and profile photos

## Team registration

1. Open the running application's `/uninest/Admin/web/jsp/Register.html` page.
2. Each member uses their own name, email and strong password. The account is created pending approval.
3. An existing active admin signs in and opens **Admin access requests** in the sidebar (or `/uninest/AdminAccessServlet`). Confirm the person's identity, then approve or decline.
4. An approved member signs in with their own email and password, selecting the Admin role.

Approval does not depend on display names. All active admins may review pending requests; approvals do not grant a super-admin access level. Decisions are recorded as notifications for the reviewing admin. Approval only operates on pending accounts and cannot activate suspended or previously declined accounts. Public registration never automatically grants administrator access.

Members must reach the same running server to share accounts. `localhost` refers to the member's own PC, not yours. A GitHub push shares source code, not the database or running website. Use the deployed HTTPS URL for remote members. On a trusted local network, use the host PC's address and configured Tomcat port; avoid real credentials over unencrypted public networks. Do not open MySQL to the public internet.

Fresh independent installations require an owner-controlled first admin setup as described in DEPLOYMENT.md. They do not inherit your local accounts or profile images.

### First admin on a database with no active admin

If nobody can approve requests yet (for example a new Render database), the owner approves exactly one request directly:

1. Register the admin at `Register.html` as above, so the account is pending.
2. Local database: run `tools\approve-admin.ps1 -Email you@example.com`. It uses the `UNINEST_DB_*` settings and only activates a pending admin request; it never changes students, landlords, declined or suspended accounts.
3. Hosted database (Render): run this once in the database's SQL console, replacing the email:

   ```sql
   UPDATE users u JOIN admins a ON a.user_id = u.user_id
   SET u.status = 'active', a.admin_position = 'Administrator'
   WHERE u.email = 'you@example.com' AND u.role = 'admin' AND u.status = 'pending_verification';
   ```

After that, approve every other admin from **Admin access requests** in the portal.

## Profile picture

Open Admin Profile, choose a JPEG/PNG up to 5 MB, then click **Upload Picture**. Pictures are resized to at most 1024 pixels on their longest side and submitted with a session security token. After success the page's avatars refresh; reopening the profile loads the stored image.

Configure `UNINEST_UPLOAD_DIR` as a writable persistent directory and back it up. Images are stored by immutable user ID, not display name. GitHub intentionally does not receive uploaded profile pictures. A cloud host needs persistent storage for them to survive restarts/redeployments.

## Files changed for this fix

- `AdminRegistration.java`: transactional approve/decline policy and request notification destination.
- `AdminAccessServlet.java`: authenticated pending-request queue, escaped content, CSRF-protected actions.
- `AdminRegisterRequestServlet.java`: corrected documentation of the existing pending-account registration behavior.
- `Register.html`: explains team registration and approval.
- `Profile.html`: explicit POST upload form and visible selected-file control.
- `Admin/web/css/style.css`: stops the header's blanket image-hiding rule from hiding profile pictures.
- `portal-identity.js`: admin request navigation, explicit photo submission, client resize, CSRF header and session-expiry feedback.
- `ProfileImageServlet.java`: deterministic image MIME types independent of Windows file associations.
- `DatabaseUpgradeChecks.java`: approval authorization and state-transition tests.
- `tools/check-admin-team-live.cjs`: isolated real-browser registration, approval, sign-in and profile-image persistence checks.
- `.gitignore`: excludes runtime data, backups, personal-data screenshots and local tooling from source publication.
- `Dockerfile`: packages CSRF, gallery and map assets required by the updated application.

Earlier listing/photo/map changes are documented in LISTING-PHOTOS-SETUP.md. No new database migration is needed for this admin fix; existing final-term migrations are still required on teammates' installations.
