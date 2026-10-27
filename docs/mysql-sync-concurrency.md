# MySQL synchronization concurrency

Plan 45 (13 October 2026) reproduced the historical Torino-delete/Paris-insert
deadlock against a local MySQL 8.4 copy with Connector/J 9.2.0. The collection
unlink query on `dcat_concept_prefLabel` scanned its primary index because
`(concept_id,nodeID)` had no index, locking rows belonging to other catalogues.
Adding `idx_preflabel_concept_node` lets the same controlled case complete.
The corresponding additive migration is
`docker/migrations/plan45-pref-label-index.sql`; check the named index before
applying it once, after a backup. Do not reapply the DCAT 3 migration.
The JPA mapping also declares the index for new schemas.

`DatasetPersistence` owns the DB units for add/delete/replace and initial import.
Replacement removes and reinserts a dataset with one commit, preserving the
dataset ID and rebinding the series' parent ID. A failed insert rolls back the
delete. The initial import commits one page atomically. Each retry creates and
closes a fresh EntityManager and reloads a pristine internal snapshot, including
DCAT property URIs, transient catalogue IDs, and Orion configuration subtypes.
Connector objects and failed persistence contexts are not reused for inserts.

`DatabaseTransaction` retries only an underlying SQL error **1213 with SQLSTATE
40001**, at most **3 total attempts** by default. Configure the total with JVM
property `-Didra.mysql.deadlock.maxAttempts=1..10`. Backoff is 500 ms, then
1000 ms, plus 0–499 ms jitter (doubling for further configured attempts).
Lock timeout 1205, duplicate keys, and other permanent failures propagate.
The warning reports the attempt count, without credentials or dataset contents.
No global isolation level or timeout is changed. Cascade gap locks can still
produce deadlocks; the index is not a guarantee against every conflict.

Solr updates happen after DB commit and outside retry. Initial import no longer
rolls back the shared Solr server or repeats the whole import after a persistence
error. Orion URL preparation is a DB-only callback; generated configuration IDs
are available before preparing the URLs. Definitive persistence/index errors
propagate to the sync, preserve its prior lastUpdateDate, mark the catalogue
OFFLINE, release its lock and stop the broker export and success path.

This does not make a complete synchronization atomic across MySQL, Solr, RDF4J
and NGSI-LD. A Solr failure after commit leaves a DB unit to reconcile during a
subsequent sync. Existing source-generated child identifiers may change on a
full replacement. No outbox or global synchronization mutex was introduced.
Solr batching remains plan 46.

Periodic and admin triggers use Quartz JobKey `<catalogueId>/jobs`, and
`OdmsSynchJob` retains `@DisallowConcurrentExecution`: triggers for the same
catalogue serialize while different catalogue keys can execute concurrently.
The guarantee applies to this scheduler; it does not cover multiple independent
application instances sharing the same DB.

Run the full suite with the bridge fixture and isolated MySQL tests:

```powershell
mvn -o '-Dplan45.mysql=true' '-Dngsi.fixture=../../NGSI-LDBrokerManager/target/dcat3-entities.json' package
```

`MysqlConcurrencyTest` uses only `127.0.0.1:13345/plan45_fixture`, with explicit
test credentials; its create/drop schema never targets the original database.
Without `plan45.mysql=true` this integration class is skipped; the full plan 45
verification enables it.

For rollback, restore the prior local Idra image. The index is additive and can
remain; if dropping it, stop synchronizations first and use the commented
rollback statement in the migration. Image rollback does not restore catalogues,
broker entities or RDF graphs changed by later live syncs; those require the
corresponding snapshots and a separate data restoration procedure.


During plan 45 live validation, Paris returned total_count=490 but only 440 unique
IDs during a slow, unordered paginated fetch. The existing connector interpreted
the incomplete list as 50 deletions; all 50 were subsequently confirmed in the
public source. OpenDataSoft now requests sort=dataset_id and requires a stable
count and complete, unique identities before returning a corpus. Null/missing
pages and conversion errors abort rather than produce a deletion delta. Two
HTTP fixture regressions fail before the change and pass after it. A partial root-only recovery was insufficient for startup indexing and was
abandoned. The complete, checksum-verified MySQL snapshot restored all original
IDs and child metadata; DB/search inventories were checked before any new sync.
Historical partial-recovery scripts are disabled and must not be rerun. Source pagination is independent of the MySQL deadlock diagnosis.


An insert also checks the persisted `(nodeId, identifier)` identity before writing,
including initial import pages. A stale/empty Solr index cannot cause a second
logical dataset to be inserted: the error propagates for index reconciliation.
This guard operates within the bounded DB transaction; it does not add a global
lock or a cross-instance uniqueness guarantee for simultaneous new identities.
