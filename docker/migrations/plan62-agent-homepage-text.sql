-- Widen dcat_agent.homepage: a single publisher homepage longer than 255 characters
-- made the whole catalogue synchronisation fail (Data too long for column 'homepage').
-- Idempotent; existing values are kept unchanged and no other column is touched.
SET @plan62_homepage_type = (SELECT DATA_TYPE FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='dcat_agent' AND column_name='homepage');
SET @plan62_homepage_ddl = IF(@plan62_homepage_type='varchar',
  'ALTER TABLE dcat_agent MODIFY COLUMN homepage TEXT NULL', 'SELECT 1');
PREPARE plan62_homepage_statement FROM @plan62_homepage_ddl;
EXECUTE plan62_homepage_statement;
DEALLOCATE PREPARE plan62_homepage_statement;
