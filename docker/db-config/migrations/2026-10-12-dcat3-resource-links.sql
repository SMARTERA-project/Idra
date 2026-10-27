-- DCAT 3 resource identity and version/series links. Apply before deploying the new WAR.
-- No existing rows are removed; source IRIs remain distinct from internal database IDs.
SET @task_schema = DATABASE();
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_dataset' AND column_name='resourceUri'), 'SELECT 1', 'ALTER TABLE dcat_dataset ADD COLUMN resourceUri varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl;
EXECUTE task_stmt;
DEALLOCATE PREPARE task_stmt;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_dataset_series' AND column_name='resourceUri'), 'SELECT 1', 'ALTER TABLE dcat_dataset_series ADD COLUMN resourceUri varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl;
EXECUTE task_stmt;
DEALLOCATE PREPARE task_stmt;
CREATE TABLE IF NOT EXISTS dcat_dataset_hascurrentversion (
  dataset_id varchar(255) NOT NULL, nodeID varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_id, nodeID),
  FOREIGN KEY (dataset_id, nodeID) REFERENCES dcat_dataset(dataset_id, nodeID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_dataset_previousversion (
  dataset_id varchar(255) NOT NULL, nodeID varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_id, nodeID),
  FOREIGN KEY (dataset_id, nodeID) REFERENCES dcat_dataset(dataset_id, nodeID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_dataset_prev (
  dataset_id varchar(255) NOT NULL, nodeID varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_id, nodeID),
  FOREIGN KEY (dataset_id, nodeID) REFERENCES dcat_dataset(dataset_id, nodeID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_dataset_next (
  dataset_id varchar(255) NOT NULL, nodeID varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_id, nodeID),
  FOREIGN KEY (dataset_id, nodeID) REFERENCES dcat_dataset(dataset_id, nodeID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_series_first (
  dataset_series_id varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_series_id),
  FOREIGN KEY (dataset_series_id) REFERENCES dcat_dataset_series(dataset_series_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_series_last (
  dataset_series_id varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_series_id),
  FOREIGN KEY (dataset_series_id) REFERENCES dcat_dataset_series(dataset_series_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_series_prev (
  dataset_series_id varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_series_id),
  FOREIGN KEY (dataset_series_id) REFERENCES dcat_dataset_series(dataset_series_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE IF NOT EXISTS dcat_series_next (
  dataset_series_id varchar(255) NOT NULL, resourceUri varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_series_id),
  FOREIGN KEY (dataset_series_id) REFERENCES dcat_dataset_series(dataset_series_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_location' AND column_name='dataset_series_id'), 'SELECT 1', 'ALTER TABLE dcat_location ADD COLUMN dataset_series_id varchar(255) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_periodoftime' AND column_name='dataset_series_id'), 'SELECT 1', 'ALTER TABLE dcat_periodoftime ADD COLUMN dataset_series_id varchar(255) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_vcard' AND column_name='dataset_series_id'), 'SELECT 1', 'ALTER TABLE dcat_vcard ADD COLUMN dataset_series_id varchar(255) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
CREATE TABLE IF NOT EXISTS dcat_series_legislation (
  dataset_series_id varchar(255) NOT NULL, applicableLegislation varchar(2048) DEFAULT NULL,
  KEY idx_owner (dataset_series_id), FOREIGN KEY (dataset_series_id) REFERENCES dcat_dataset_series(dataset_series_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_data_service' AND column_name='resourceUri'), 'SELECT 1', 'ALTER TABLE dcat_data_service ADD COLUMN resourceUri varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_catalogue_record' AND column_name='resourceUri'), 'SELECT 1', 'ALTER TABLE dcat_catalogue_record ADD COLUMN resourceUri varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
CREATE TABLE IF NOT EXISTS dcat_version_note (
  dataset_id varchar(255) NOT NULL, nodeID varchar(255) NOT NULL, value LONGTEXT, language varchar(255),
  KEY idx_owner (dataset_id,nodeID), FOREIGN KEY (dataset_id,nodeID) REFERENCES dcat_dataset(dataset_id,nodeID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_dataset_series' AND column_name='brokerId'), 'SELECT 1', 'ALTER TABLE dcat_dataset_series ADD COLUMN brokerId varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_data_service' AND column_name='brokerId'), 'SELECT 1', 'ALTER TABLE dcat_data_service ADD COLUMN brokerId varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
CREATE TABLE IF NOT EXISTS dcat_record_language (
  catalogue_record_id varchar(255) NOT NULL, languageUri varchar(2048),
  KEY idx_owner (catalogue_record_id), FOREIGN KEY (catalogue_record_id) REFERENCES dcat_catalogue_record(catalogue_record_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
SET @task_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@task_schema AND table_name='dcat_distribution' AND column_name='resourceUri'), 'SELECT 1', 'ALTER TABLE dcat_distribution ADD COLUMN resourceUri varchar(2048) NULL');
PREPARE task_stmt FROM @task_ddl; EXECUTE task_stmt; DEALLOCATE PREPARE task_stmt;
