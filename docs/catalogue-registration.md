# Catalogue registration identities

Catalogue IDs identify persisted configurations. The display name is not unique:
different catalogue endpoints may legitimately use the same name. Name filters
expand to every matching catalogue ID, then apply the existing active-catalogue
visibility rules. An explicit catalogue-ID filter remains scoped to that ID.

For a new DCATDUMP, Idra allocates a technical host before either the active or
inactive insert. It does not depend on the display name, registration time,
publisher or common SPARQL host. The allocated value is persisted and activation
updates that row without generating a new value. Existing persisted identities
are not rewritten automatically.

Two DCATDUMP configurations with the same nonblank dumpURL are duplicates. The
complete URL, including the case-sensitive query, is compared; different queries
on the same endpoint remain distinct. File-based registrations have separate
technical identities. Registrations within one JVM are serialized through the
identity check and insert. Database constraints still enforce endpoint/URL
uniqueness; this is not a claim of distributed transactionality across instances.

If an imported catalogue omits `homepage`, its supplied `host` is used. An explicit
homepage is preserved. Other required fields retain their validation.

Typed ORION/SPARQL additional configurations are decoded even without inline dump
content. Registration nevertheless requires supplied dump content or an upload;
a file path from another server is not fetched or treated as local content. Missing
content returns HTTP400. Configuration credentials and live source availability
are not inferred from remote examples.

## Existing databases

Before installing this change, run
`docker/migrations/plan58-catalogue-display-names.sql` against the selected Idra
schema. It drops only single-column UNIQUE constraints on `odms.name` and ensures
a nonunique name index. Catalogue IDs, rows, names, hosts, dump URLs, other indexes
and data are unchanged. The script is idempotent; the bootstrap schema uses the
same nonunique index for new installations. Hibernate update does not reliably
remove an old unique constraint on its own.

Keep a logical backup and validate on a copy. Restoring the old unique constraint
after homonymous catalogues have been added requires resolving those names first;
do not delete or rename configurations automatically as a rollback. Rolling back
only the application also restores the old single-ID name-filter behavior.

Plan58 verification was performed only on isolated databases and the QA backend.
The original runtime/schema was not migrated or replaced. The complete lifecycle
and DCAT3 round-trip matrices remain the separate plans56/57.
