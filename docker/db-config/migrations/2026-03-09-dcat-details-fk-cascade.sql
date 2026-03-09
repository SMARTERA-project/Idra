ALTER TABLE dcat_details
  DROP FOREIGN KEY FK_det_dataset;

ALTER TABLE dcat_details
  ADD CONSTRAINT FK_det_dataset
  FOREIGN KEY (dataset_id, nodeID)
  REFERENCES dcat_dataset(dataset_id, nodeID)
  ON DELETE CASCADE;
