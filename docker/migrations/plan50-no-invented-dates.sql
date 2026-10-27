-- Plan 50: Idra no longer invents 1970-01-01T00:00:00Z when a source has no issued/modified date.
-- Removes the column defaults and clears the values written by older versions (exactly that placeholder).
-- Other dates are not touched. Safe to repeat; the application also clears the placeholder when it reads it.
ALTER TABLE dcat_distribution ALTER COLUMN releaseDate SET DEFAULT NULL;
ALTER TABLE dcat_distribution ALTER COLUMN updateDate SET DEFAULT NULL;
UPDATE dcat_dataset SET releaseDate = '' WHERE releaseDate = '1970-01-01T00:00:00Z';
UPDATE dcat_dataset SET updateDate = '' WHERE updateDate = '1970-01-01T00:00:00Z';
UPDATE dcat_distribution SET releaseDate = '' WHERE releaseDate = '1970-01-01T00:00:00Z';
UPDATE dcat_distribution SET updateDate = '' WHERE updateDate = '1970-01-01T00:00:00Z';
