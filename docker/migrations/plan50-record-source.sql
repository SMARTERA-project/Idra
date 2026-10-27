-- Apply to the selected schema before installing the catalogue record dct:source reader (plan 50, step 3).
-- Nullable; existing records are not rewritten and are filled at the next synchronisation. Safe to repeat.
SET @plan50_record_present = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='dcat_catalogue_record' AND column_name='sourceRecord');
SET @plan50_record_ddl = IF(@plan50_record_present=0,
  'ALTER TABLE dcat_catalogue_record ADD COLUMN sourceRecord TEXT NULL', 'SELECT 1');
PREPARE plan50_record_statement FROM @plan50_record_ddl;
EXECUTE plan50_record_statement;
DEALLOCATE PREPARE plan50_record_statement;
