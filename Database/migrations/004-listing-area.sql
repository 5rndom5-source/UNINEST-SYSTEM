-- Apply after 003-listing-photo-tour.sql, before deploying the area-aware application.
-- Additive only: do not infer an area from a street address, suburb or city.
-- Safe to rerun. Existing listing, image, booking and review rows are not updated.
SET @area_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='rooms' AND column_name='area');
SET @area_sql = IF(@area_exists=0,
 'ALTER TABLE rooms ADD COLUMN area VARCHAR(100) NULL DEFAULT NULL',
 'SELECT 1');
PREPARE area_migration FROM @area_sql;
EXECUTE area_migration;
DEALLOCATE PREPARE area_migration;
