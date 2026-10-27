-- Run against an explicitly selected schema, before installing the record reader.
-- Preserve dataset/distribution standards. Only unowned legacy record profiles
-- with exactly one possible parent are backfilled; ambiguous data stops migration.
DELIMITER $$
DROP PROCEDURE IF EXISTS plan62_record_profile_owner$$
CREATE PROCEDURE plan62_record_profile_owner()
BEGIN
  DECLARE finished INT DEFAULT 0;
  DECLARE index_name_to_drop VARCHAR(255);
  DECLARE old_indexes CURSOR FOR
    SELECT index_name FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='dcat_catalogue_record'
      AND non_unique=0 AND index_name<>'PRIMARY'
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='nodeID';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='dcat_standard' AND column_name='catalogue_record_id') THEN
    ALTER TABLE dcat_standard ADD COLUMN catalogue_record_id VARCHAR(255) NULL;
  END IF;

  IF EXISTS (
    SELECT 1 FROM dcat_standard s JOIN dcat_catalogue_record r
      ON s.nodeID=CAST(r.nodeID AS CHAR)
    WHERE s.dataset_id IS NULL AND s.distribution_id IS NULL AND s.catalogue_record_id IS NULL
    GROUP BY s.standard_id HAVING COUNT(*)>1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ambiguous legacy record profile owner; explicit mapping required';
  END IF;

  UPDATE dcat_standard s JOIN dcat_catalogue_record r ON s.nodeID=CAST(r.nodeID AS CHAR)
    SET s.catalogue_record_id=r.catalogue_record_id
    WHERE s.dataset_id IS NULL AND s.distribution_id IS NULL AND s.catalogue_record_id IS NULL;

  OPEN old_indexes;
  indexes_loop: LOOP
    FETCH old_indexes INTO index_name_to_drop;
    IF finished=1 THEN LEAVE indexes_loop; END IF;
    SET @plan62_record_ddl=CONCAT('ALTER TABLE dcat_catalogue_record DROP INDEX `',REPLACE(index_name_to_drop,'`','``'),'`');
    PREPARE plan62_record_statement FROM @plan62_record_ddl;
    EXECUTE plan62_record_statement;
    DEALLOCATE PREPARE plan62_record_statement;
  END LOOP;
  CLOSE old_indexes;

  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
    WHERE table_schema=DATABASE() AND table_name='dcat_standard' AND column_name='catalogue_record_id'
      AND referenced_table_name='dcat_catalogue_record' AND referenced_column_name='catalogue_record_id') THEN
    ALTER TABLE dcat_standard ADD CONSTRAINT FK_plan62_standard_record
      FOREIGN KEY (catalogue_record_id) REFERENCES dcat_catalogue_record(catalogue_record_id);
  END IF;
END$$
CALL plan62_record_profile_owner()$$
DROP PROCEDURE plan62_record_profile_owner$$
DELIMITER ;
