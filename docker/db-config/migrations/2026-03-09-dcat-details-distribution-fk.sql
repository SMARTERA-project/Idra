ALTER TABLE dcat_details
  ADD COLUMN distribution_id VARCHAR(255) NULL;

ALTER TABLE dcat_details
  ADD KEY FK_det_distribution (distribution_id);

ALTER TABLE dcat_details
  ADD CONSTRAINT FK_det_distribution
  FOREIGN KEY (distribution_id)
  REFERENCES dcat_distribution(id)
  ON DELETE CASCADE;
