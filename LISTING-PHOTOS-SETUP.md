# Listing editor, room photos and maps

## Deployment

1. Back up the existing MySQL database and the configured upload directory.
2. Apply `Database/migrations/003-listing-photo-tour.sql` to the existing database. It only creates new tables; it preserves listing IDs and existing photo URLs.
3. Build the WAR with `tools/check-upgrades.ps1` and restart the application with that WAR.
4. Do not run `Database/Uninest_DB.sql` on an existing database: it is the destructive fresh-install schema.

The form retains its existing field names and listing save endpoint. Additional details are in `listing_details`. The gallery response adds `listing.rooms` while retaining the legacy `images` array. Existing categories become room groups when read; saving the editor persists those groups.

Photos upload individually to an owner-scoped staging table. The browser accepts JPEG, PNG and WebP up to 10 MB and converts them to JPEG at a maximum 1600-pixel longest edge. The server independently decodes the result, checks dimensions, and strips metadata by re-encoding. Staging tokens expire after 24 hours and expired rows are removed during subsequent uploads. A landlord can have at most 60 staged photos. Production maintenance should also periodically delete expired staging rows when the site is idle.

Saving requires 5-60 photos, exactly one cover and valid room assignments. Empty room groups are allowed with warnings, but are hidden from the student photo tour. The save transaction checks image ownership and records all changes together. Existing listings with fewer than five photos remain viewable; their next editor save requires five.

Removed photo records are no longer served. Old image files are retained on disk rather than deleted before a transaction has committed. Include an orphan-file retention/cleanup policy in production storage maintenance; do not indiscriminately delete the upload directory.

## Google Maps

The address fields generate Google Maps search and directions links immediately, without a key. The embedded map uses the official Maps Embed API `place` mode and loads only when the user selects **Preview map**. Editing the address updates an already-open preview.

To enable the embedded map:

1. Create a Google Cloud project and enable **Maps Embed API**.
2. Google requires a billing account even though Embed usage is currently offered at no charge. Do not enable paid Places/Geocoding services for this implementation.
3. Create a dedicated browser API key. Restrict its APIs to **Maps Embed API**, and its application to **Websites** with only your real origins, for example `https://uninest.co.za/*` and the actual local preview origin during development.
4. Configure `UNINEST_GOOGLE_MAPS_EMBED_KEY` in the server environment, then restart Tomcat. Never use an unrestricted or server-service key here. Browser keys are intentionally visible to clients; website and API restrictions provide protection.
5. Verify the marker for a real property address. An address search is not proof of the property's exact location, ownership, or availability. Incorrect/restricted keys can display Google's error inside the iframe; the external map links remain available.

Official references:
- https://developers.google.com/maps/documentation/embed/quickstart
- https://developers.google.com/maps/documentation/embed/embedding-map
- https://developers.google.com/maps/api-security-best-practices

## Verification

- `tools/check-upgrades.ps1`: build and existing Java regression checks.
- `tools/check-database-upgrades.ps1`: disposable database tests, including room groups, captions, ordering, cover, ownership and validation.
- `tools/check-listing-photos.cjs`: desktop/mobile browser tests with mocked API responses. Google requests are blocked; this verifies URL wiring, not a live Google key.
- `tools/start-listing-test-preview.ps1 -DatabaseName <disposable-database>`: isolated local test server on port 8093 with test-only uploads.

No paid services are enabled by this change. The embedded map cannot be verified live until a correctly restricted Google Maps Embed key is configured.

## File-by-file changes

| File | Change |
| --- | --- |
| `Landlord/listings.html` | Six labeled sections, additional address/pricing/contact fields, map host and save/cancel actions. |
| `Landlord/listing-form.css` | Scoped 640px form, 44px fields, spacing, responsive pairs, amenity chips and photo editor styling. |
| `Landlord/gallery-upload.js` | Room/photo state, validation, resizing, real upload progress/retry, cover, captions, move/reorder, edit hydration and atomic save manifest. |
| `Landlord/script.js` | Edit event integration, save button wording and full-address map links. |
| `UserDashboard/script.js` | Student card map links resolve the full saved address. |
| `AddPropertyServlet.java` | Saves extended fields and photo manifest in the existing listing transaction; returns actionable save failures. |
| `ListingEditor.java` | Validates ownership/metadata and persists room groups, photo assignments, cover/order and extra listing fields; reads the nested model. |
| `ListingPhotoUploadServlet.java` | Authenticated, CSRF-protected image staging with decode/dimension checks, quota, expiry and idempotent retry tokens. |
| `PropertyGalleryServlet.java` | Adds `listing.rooms` and listing details without removing the older `images` response. |
| `PropertyImageServlet.java` | Prevents direct access to removed or unauthorized pending photos. |
| `ListingMapServlet.java` | Resolves authorized saved addresses into Google Maps links, including suburb/city. |
| `MapsConfigServlet.java` | Exposes only the dedicated Maps Embed browser key configuration. |
| `property-gallery.js` | Hero, room tour, active room navigation, fullscreen keyboard/swipe navigation and captions. |
| `property-gallery.css` | Responsive gallery, placeholders, sticky room navigation and fullscreen layout. |
| `maps.js` | Address-linked search/directions and opt-in interactive Embed API preview. |
| `maps.css` | Responsive map tool and action styling. |
| `Database/migrations/003-listing-photo-tour.sql` | Additive tables for listing details, room groups, photo metadata and staged uploads. |
| `Database/Uninest_DB.sql` | Same tables in the fresh-install schema. |
| `pom.xml` | Packages the new map/gallery assets. |
| `deployment.env.example` | Documents the restricted Maps Embed browser key environment variable. |
| `src/test/java/com/uninest/ListingPhotoChecks.java` | Persistence, legacy compatibility, ownership and validation tests. |
| `src/test/java/com/uninest/DatabaseUpgradeChecks.java` | Runs the new database checks with the existing checks. |
| `tools/check-database-upgrades.ps1` | Adds servlet test dependency and retains database connection options. |
| `tools/check-listing-photos.cjs` | Mocked browser tests for uploads, form dimensions, viewer and map URL wiring. |
| `tools/check-listing-live.cjs` | Real isolated upload/create/edit/render/privacy/map-link journey. |
| `tools/start-listing-test-preview.ps1` | Starts test-only Tomcat/database/uploads on port 8093. |
| `LISTING-PHOTOS-SETUP.md` | Migration, storage limitations, Google setup and this change summary. |
