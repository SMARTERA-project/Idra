package it.eng.idra.cache;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.exception.DatasetNotFoundException;
import java.io.IOException;
import java.util.*;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;

/** Explicit, thread-confined sync context. The shared Solr commit is not catalogue isolation. */
public final class SyncIndexBatch implements AutoCloseable {
  private static final org.apache.logging.log4j.Logger logger =
      org.apache.logging.log4j.LogManager.getLogger(SyncIndexBatch.class);
  private final SolrClient client;
  private final int nodeId;
  private final int maxDocuments;
  private final Map<String, DcatDataset> known = new HashMap<>();
  private final Map<String, Pending> pending = new LinkedHashMap<>();
  private boolean closed;
  private int queuedDocuments;
  private long databaseNs, prepareNs, writeNs, commitNs;
  private int commits, requests, peakDocuments;

  private static final class Pending {
    final DcatDataset dataset; // null means delete; retained until commit succeeds
    SolrInputDocument document;
    Pending(DcatDataset dataset) { this.dataset = dataset; }
    int size() { return document == null ? 1 : documentCount(document); }
  }

  SyncIndexBatch(SolrClient client, int nodeId, Collection<DcatDataset> present, int maxDocuments) {
    if (maxDocuments < 1) throw new IllegalArgumentException("Batch limit must be positive");
    this.client = client; this.nodeId = nodeId; this.maxDocuments = maxDocuments;
    for (DcatDataset dataset : present) { checkCatalogue(dataset); known.put(identifier(dataset), dataset); }
  }

  public DcatDataset find(String identifier) throws DatasetNotFoundException {
    checkOpen(); DcatDataset dataset = known.get(identifier);
    if (dataset == null) throw new DatasetNotFoundException("Dataset not found in sync context: " + identifier);
    return dataset;
  }

  /** Called only after the DB unit succeeds. A failed send can be replayed without a DB retry. */
  public void upsert(DcatDataset committed) throws IOException, SolrServerException {
    checkOpen(); checkCatalogue(committed);
    known.put(identifier(committed), committed);
    Pending update = new Pending(committed);
    Pending previous = pending.put(committed.getId(), update);
    if (previous != null) queuedDocuments -= previous.size();
    queuedDocuments++; // keep the persisted ID even if document preparation fails
    long start = System.nanoTime();
    try { update.document = committed.toDoc(); }
    finally { prepareNs += System.nanoTime() - start; }
    queuedDocuments += update.size() - 1;
    peakDocuments = Math.max(peakDocuments, queuedDocuments);
    if (queuedDocuments >= maxDocuments) flush();
  }

  public void delete(DcatDataset deleted) throws IOException, SolrServerException {
    checkOpen(); checkCatalogue(deleted); known.remove(identifier(deleted));
    deleteRoot(deleted.getId());
  }

  void deleteRoot(String id) throws IOException, SolrServerException {
    checkOpen();
    Pending previous = pending.put(id, new Pending(null));
    if (previous != null) queuedDocuments -= previous.size();
    queuedDocuments++;
    peakDocuments = Math.max(peakDocuments, queuedDocuments);
    if (queuedDocuments >= maxDocuments) flush();
  }

  /** All affected trees are deleted before add. Retain the entire bounded set on any failure. */
  public void flush() throws IOException, SolrServerException {
    checkOpen(); if (pending.isEmpty()) return;
    List<String> deletes = new ArrayList<>(); List<SolrInputDocument> documents = new ArrayList<>();
    for (Map.Entry<String, Pending> entry : pending.entrySet()) {
      deletes.add("_root_:\"" + ClientUtils.escapeQueryChars(entry.getKey()) + "\" AND nodeID:" + nodeId);
      Pending update = entry.getValue();
      if (update.dataset != null) {
        if (update.document == null) {
          long start = System.nanoTime();
          try { update.document = update.dataset.toDoc(); }
          finally { prepareNs += System.nanoTime() - start; }
        }
        documents.add(update.document);
      }
    }
    long start = System.nanoTime();
    try {
      UpdateRequest deletion = new UpdateRequest();
      for (String query : deletes) deletion.deleteByQuery(query);
      requests++; deletion.process(client);
      if (!documents.isEmpty()) { requests++; client.add(documents); }
    } finally { writeNs += System.nanoTime() - start; }
    start = System.nanoTime();
    try { requests++; client.commit(); commits++; }
    finally { commitNs += System.nanoTime() - start; }
    pending.clear(); queuedDocuments = 0;
  }

  static int documentCount(SolrInputDocument document) {
    int count = 1;
    if (document.hasChildDocuments()) for (var child : document.getChildDocuments()) count += documentCount(child);
    return count;
  }
  private static String identifier(DcatDataset dataset) { return dataset.getIdentifier().getValue(); }
  private void checkCatalogue(DcatDataset dataset) {
    if (!Integer.toString(nodeId).equals(dataset.getNodeId())) throw new IllegalArgumentException("Dataset belongs to another catalogue");
  }
  void validate(int expectedNodeId, DcatDataset dataset) {
    checkOpen();
    if (expectedNodeId != nodeId) throw new IllegalArgumentException("Batch belongs to another catalogue");
    checkCatalogue(dataset);
  }
  private void checkOpen() { if (closed) throw new IllegalStateException("Sync index context is closed"); }
  public String metrics() {
    return String.format(Locale.ROOT, "catalogue=%d commits=%d requests=%d databaseMs=%.3f prepareMs=%.3f writeMs=%.3f commitMs=%.3f peakDocuments=%d limit=%d",
        nodeId, commits, requests, databaseNs/1e6, prepareNs/1e6, writeNs/1e6, commitNs/1e6, peakDocuments, maxDocuments);
  }
  void databaseTime(long elapsedNs) { databaseNs += elapsedNs; }
  @Override public void close() throws IOException, SolrServerException {
    if (closed) return;
    try { flush(); }
    catch (IOException | SolrServerException | RuntimeException failure) {
      logger.error("SOLR reconciliation required catalogue=" + nodeId + " persistedIds=" + pending.keySet(), failure);
      throw failure;
    }
    finally {
      closed = true; pending.clear(); known.clear();
      logger.info("SOLR sync batch " + metrics());
    }
  }
}
