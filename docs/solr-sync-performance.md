# Solr synchronization batching

Synchronization uses an explicit `SyncIndexBatch` for one catalogue and one job.
The database transaction/retry behavior from plan 45 is unchanged. Only successful
DB inserts/replacements/deletions enter the index queue. Public methods without a
batch still commit immediately, including notification and datalet callers.

Identity lookups use the catalogue snapshot held by the batch, updated after each
successful DB unit. CKAN incremental synchronization now loads that snapshot after
connector fetch. The index is flushed before version detection, broker export,
dump generation and RDF import. Full synchronization still adds/replaces remote
datasets without deleting absent datasets; explicit delta deletions are preserved.

The JVM property `idra.solr.sync.maxDocuments` defaults to 1000 (range 1–100000).
The count includes every nested descendant. Trees are indivisible: the queue can
exceed the target by one tree and a single large dataset flushes immediately.
The identity map is proportional to catalogue size and released on close; the
pending document queue is bounded by the target plus the largest tree, rather
than the size of the catalogue. The limit does not promise a byte ceiling.

Each flush deletes only the affected `_root_` trees within `nodeID`, sends the
replacement trees in one add request and performs a hard commit. The existing
Solr auto-commit configuration remains unchanged. A shared commit can make other
jobs' already submitted updates visible. There is no catalogue-level index
transaction and no global rollback.

On index failure the entire pending set is retained and can be replayed, including
deletes, because replacement first deletes each affected tree. Closing after a
failure attempts one reconciliation flush. The original failure still propagates:
the catalogue stays OFFLINE, lastUpdateDate is preserved and success/broker/dump
paths are skipped. If close also fails, the log lists the persisted IDs needing
reconciliation, and the context is released.

After the index becomes available, an operator can invoke
`MetadataCacheManager.reconcileDatasetIndex(nodeId, persistedIds)` in the existing
application context while that catalogue's job is idle. This reads authoritative
DB rows, upserts existing datasets and deletes trees whose DB row is absent. It
never writes the DB or deletes unrelated IDs/catalogues. No unauthenticated
recovery endpoint was added. Restart recovery also follows the application's
existing DB-to-index initialization. A full sync alone is not a recovery for an
empty index: the DB identity guard intentionally rejects duplicate inserts.

Logs split fetch, DB, document preparation, index submission/commit, broker, dump
and RDF import. `SYNC persistence/index/RDF-distributions` is the combined loop;
subtracting its submetrics does not independently measure every RDF download.
Failures in legacy downstream RDF/broker code retain their existing semantics;
this does not provide a distributed transaction.

Validated and deployed locally on 14 October 2026. The fixed Paris refresh
benchmark reduced 490 commits to 14 and mean index time from 62.932 to 6.017
seconds (two repetitions; all 13,760 documents retained). Live full syncs used
3 commits for Torino and 15 for Paris; DB/network/RDF remain separate costs.
All original dataset IDs survived; DB/search/broker/dump/RDF agreed on 266/468
and 490/5850 respectively, with valid RDF dates. Five existing DCAT 3 projection
fields were added to the schema after the integration test exposed the missing
previousVersion declaration. 52 tests passed including the benchmark and 51
application tests passed in the final WAR build, with no skips.

Runtime image: docker-idra:dcat3-solr-batch-20261014, SHA 4f124c9cbf275a87c83a97a7e77ce32177d9e1a2f7b5a5b8f88ad6aab0f3c433.
Distribution IDs/URI fallbacks continue to
change on full replacement. The historical HTTPS Paris graph remains intact.
MQA connectivity was subsequently restored with explicit user approval. The
service is reachable from Idra and MongoDB/S3 read-only checks passed. No new
analysis or full synchronization was triggered by the network repair.
