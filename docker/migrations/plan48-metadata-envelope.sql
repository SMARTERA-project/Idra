-- Additive only: no historical backfill, UPDATE or DELETE. Select the intended database first.
-- Safe to repeat. A non-null existing envelope column must have its type verified before deploy.
SET @plan48_present = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='dcat_dataset' AND column_name='metadataEnvelope');
SET @plan48_statement = IF(@plan48_present=0,
  'ALTER TABLE dcat_dataset ADD COLUMN metadataEnvelope LONGTEXT NULL', 'SELECT 1');
PREPARE plan48_additive FROM @plan48_statement;
EXECUTE plan48_additive;
DEALLOCATE PREPARE plan48_additive;
