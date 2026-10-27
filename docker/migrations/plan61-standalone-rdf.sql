-- Apply to the selected schema before installing the standalone resource reader.
-- Nullable source evidence; existing catalogue and DCAT rows are not rewritten.
SET @plan61_present = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='odms' AND column_name='standaloneRdf');
SET @plan61_ddl = IF(@plan61_present=0,
  'ALTER TABLE odms ADD COLUMN standaloneRdf LONGTEXT NULL', 'SELECT 1');
PREPARE plan61_statement FROM @plan61_ddl;
EXECUTE plan61_statement;
DEALLOCATE PREPARE plan61_statement;
