-- Adds the mqa_analysis_id column to the odms table.
-- Stores the id of the catalogue's MQA analysis (MongoDB ObjectId on the MQA
-- scoring service) so re-submissions append to the same 5-entry history instead
-- of creating duplicate analyses.
--
-- Idempotent and MySQL 8.4 compatible: MySQL 8.4 does not support
-- "ALTER TABLE ... ADD COLUMN IF NOT EXISTS", so guard via information_schema.

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'odms'
    AND COLUMN_NAME = 'mqa_analysis_id'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `odms` ADD COLUMN `mqa_analysis_id` VARCHAR(255) DEFAULT NULL',
  'SELECT "column odms.mqa_analysis_id already exists, skipping" AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
