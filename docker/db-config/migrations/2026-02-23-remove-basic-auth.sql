-- Removes BASIC authentication persisted data.
-- Run this once on existing databases before/after upgrading application code.

START TRANSACTION;

-- Delete local BASIC users and credentials.
DELETE FROM `user`;

-- If you want to fully remove the legacy table, uncomment the following line.
-- DROP TABLE IF EXISTS `user`;

COMMIT;
