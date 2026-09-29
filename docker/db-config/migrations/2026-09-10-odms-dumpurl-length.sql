-- Widens odms.dumpURL from VARCHAR(255) to VARCHAR(2048).
-- A dumpURL can be a SPARQL endpoint carrying an URL-encoded CONSTRUCT query
-- (e.g. the Eurostat federation against https://data.europa.eu/sparql), which
-- is well over 255 chars and used to fail with:
--   MysqlDataTruncation: Data too long for column 'dumpURL' at row 1
--
-- The UNIQUE index must be rebuilt on a prefix: the odms table is utf8mb3, so
-- an InnoDB index key caps at 3072 bytes = 1024 chars. 1000 leaves some margin.
-- Two dumpURLs sharing the first 1000 chars now collide as duplicates; in
-- practice endpoint + query differ well before that.
--
-- Run this once on existing databases before/after upgrading application code.

START TRANSACTION;

ALTER TABLE `odms` DROP INDEX `UK_r9reqhikfumkr5maisxmrp8dx`;

ALTER TABLE `odms` MODIFY COLUMN `dumpURL` varchar(2048) DEFAULT NULL;

ALTER TABLE `odms` ADD UNIQUE KEY `UK_r9reqhikfumkr5maisxmrp8dx` (`dumpURL`(1000));

COMMIT;
