-- Separate, additive migration for the proven cross-catalogue lock scan.
-- Apply once after backup and checking information_schema.statistics.
-- Does not reapply the DCAT 3 migration and does not change data/isolation/timeouts.
ALTER TABLE dcat_concept_prefLabel
  ADD INDEX idx_preflabel_concept_node (concept_id, nodeID),
  ALGORITHM=INPLACE, LOCK=NONE;
-- Rollback (only if needed, after stopping synchronizations):
-- ALTER TABLE dcat_concept_prefLabel DROP INDEX idx_preflabel_concept_node;
