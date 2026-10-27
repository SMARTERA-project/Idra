-- Apply to the selected schema before installing the catalogue source reader (plan 50, step 2).
-- Nullable source evidence; existing catalogue rows are not rewritten. Filled at the next synchronisation. Safe to repeat.
SET @plan50_present = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='odms' AND column_name='sourceMetadata');
SET @plan50_ddl = IF(@plan50_present=0,
  'ALTER TABLE odms ADD COLUMN sourceMetadata LONGTEXT NULL', 'SELECT 1');
PREPARE plan50_statement FROM @plan50_ddl;
EXECUTE plan50_statement;
DEALLOCATE PREPARE plan50_statement;
