-- Run against the selected Idra schema before installing the registration fix.
-- Display names may repeat; identities remain the catalogue ID/endpoint/dumpURL.
-- Does not change any row, host, dump URL, primary key or endpoint constraint.
SET @plan58_schema = DATABASE();
SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(index_name, '`', '``'), '`') SEPARATOR ', ')
INTO @plan58_drop_names
FROM (
  SELECT index_name
  FROM information_schema.statistics
  WHERE table_schema = @plan58_schema AND table_name = 'odms'
    AND non_unique = 0 AND index_name <> 'PRIMARY'
  GROUP BY index_name
  HAVING COUNT(*) = 1 AND MIN(column_name) = 'name'
) AS name_constraints;
SET @plan58_ddl = IF(@plan58_drop_names IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE odms ', @plan58_drop_names));
PREPARE plan58_statement FROM @plan58_ddl;
EXECUTE plan58_statement;
DEALLOCATE PREPARE plan58_statement;

SELECT COUNT(*) INTO @plan58_name_index
FROM information_schema.statistics
WHERE table_schema = @plan58_schema AND table_name = 'odms'
  AND non_unique = 1 AND seq_in_index = 1 AND column_name = 'name';
SET @plan58_ddl = IF(@plan58_name_index > 0, 'SELECT 1',
  'ALTER TABLE odms ADD INDEX idx_odms_name (name)');
PREPARE plan58_statement FROM @plan58_ddl;
EXECUTE plan58_statement;
DEALLOCATE PREPARE plan58_statement;
