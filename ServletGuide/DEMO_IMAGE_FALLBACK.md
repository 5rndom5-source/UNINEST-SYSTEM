# Demonstration image fallback

This optional feature substitutes a bundled, watermarked demonstration photo when an authorized property photo's uploaded file is missing. It does not recover the original file or provide permanent upload storage.

## Enable for the university demonstration

1. Review and approve the `feature/demo-image-fallback` pull request targeting `develop`. Do not merge while the connected Render service has automatic deployments enabled unless that deployment has been approved.
2. In the Render dashboard, open the **UniNest web service**, not the MySQL service.
3. Open **Environment**, then **Add Environment Variable**.
4. Set key `UNINEST_DEMO_IMAGE_FALLBACK` to value `true`.
5. Choose **Save only** to avoid deploying immediately. The setting does not take effect until the next deployment.
6. After approval, deploy the reviewed application commit containing this feature. If the new code is not yet built, use a new build, not just a restart of an older build.
7. Open an approved listing with a known missing upload in the Student gallery. Confirm that its substitute displays **DEMO PHOTO — NOT ACTUAL PROPERTY**, including fullscreen. Real uploaded files must still display normally.
8. Check an unauthenticated request for a pending listing's photo: it must remain 404. Check the deployment logs for image-loading errors.

To disable, change the value to `false` or remove it, then deploy/restart with the updated environment after approval. An unset variable is disabled. No database migration or data reset is needed.

Render's save options: https://render.com/docs/configure-environment-variables

## How it works

- The existing database authorization query runs first. A missing/deleted image record or inaccessible listing still returns 404. A database failure returns 503, never a demonstration photo.
- Only a valid `/PropertyImageServlet/<filename>` request whose file is definitely absent can use the fallback. Invalid paths, external URLs, profile images and unreadable/non-image files are not substituted.
- The filename deterministically selects one of the three existing `HomePage/listing-*.png` assets. The server creates a labelled PNG without writing it to disk or MySQL. At most three watermarked images are cached in application memory.
- The watermark is part of the served image, including when opened directly or displayed on a listing card. Gallery metadata also marks missing photos so the hero, room tour and fullscreen viewer provide readable labels. Existing captions, image IDs, grouping, ordering and cover selection remain unchanged.
- Responses remain `private, no-store`. Fallback responses include `X-UniNest-Demo-Photo: true`. The feature checks the filesystem on each request, so a restored original file takes precedence immediately.
- Maven already packages the HomePage assets. The tests inspect the built WAR for all three files. No new hosting service, disk or API key is required.

## Verification

Run `tools/check-upgrades.ps1` first to compile/package the current application, then `tools/check-demo-images.ps1`. The second runner uses only a newly created `uninest_demo_image_check_*` database on `127.0.0.1`, isolated uploads and temporary Tomcat instances. It restores process environment settings and stops its own servers afterwards. Test data is retained for diagnosis; production data is never used.

The runner tests disabled/enabled states, original bytes, unauthorized/deleted photos, invalid paths, a deliberately unavailable local database, deterministic substitutes, mobile/desktop Student galleries, fullscreen labels, real landlord uploads and listing edits. Packaging, watermark and original-record preservation checks run afterwards. Browser application APIs are not mocked in this suite.

`tools/check-listing-photos.cjs` provides the separate existing mocked-API gallery/upload regression suite. Its results are not a substitute for the real HTTP/MySQL suite.

Verified locally on 12 November 2026: Maven WAR build; 545 regression checks; 82 existing isolated MySQL checks (including 17 listing-photo checks); 40 mocked-API photo browser checks; and 78 new demo-image checks (11 disabled, 49 enabled, 1 unavailable-database, 17 packaging/path/watermark/preservation). Student gallery screenshots were reviewed at 390px and 1280px widths. Fresh uploads, existing photo group edits and both original and substituted image responses were exercised through real HTTP/MySQL.

## Limits

- This is a demonstration measure, not a permanent storage fix. New uploads can still disappear on a host without persistent storage, and those photos will then use labelled substitutes while the switch is enabled.
- Different missing filenames can map to the same bundled image. The substitute is not evidence of the property's appearance or amenities.
- This feature does not change profile pictures, identity documents, payments, accounts, Render configuration or database settings.
- Production rollout still requires explicit approval and a Render smoke test. Local tests do not prove the deployed container's health.
- A Docker build was not run because Docker is unavailable on the test machine. The WAR was built and tested with local Java 11 and Tomcat 9. The existing Temurin runtime provides font configuration for the watermark; verify it in the deployed container during the approved smoke test.
