-- Apply to the selected schema before installing the agent name language support (plan 50).
-- Nullable language tag of each foaf:name; existing names are not rewritten (no tag) until the next synchronisation. Safe to repeat.
SET @plan50_agent_present = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='dcat_agent_name' AND column_name='language');
SET @plan50_agent_ddl = IF(@plan50_agent_present=0,
  'ALTER TABLE dcat_agent_name ADD COLUMN language VARCHAR(64) NULL', 'SELECT 1');
PREPARE plan50_agent_statement FROM @plan50_agent_ddl;
EXECUTE plan50_agent_statement;
DEALLOCATE PREPARE plan50_agent_statement;
