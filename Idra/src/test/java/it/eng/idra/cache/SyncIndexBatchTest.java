package it.eng.idra.cache;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.exception.DatasetNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SyncIndexBatchTest {
  @TempDir Path temp;
  EmbeddedSolrServer server;
  @BeforeEach void setup() throws Exception { server = SolrSyncBenchmarkTest.open(temp.resolve("solr")); }
  @AfterEach void close() throws Exception { server.close(); }
  static class Fixture extends DcatDataset {
    final SolrInputDocument doc;
    Fixture(String node, String id, int distributions) {
      setNodeId(node); setId(id); setIdentifier(new it.eng.idra.beans.dcat.DcatProperty(
          org.apache.jena.vocabulary.DCTerms.identifier, org.apache.jena.vocabulary.RDFS.Literal, id));
      doc = new SolrInputDocument(); doc.setField("id", id); doc.setField("nodeID", node);
      doc.setField("identifier", id); doc.setField("content_type", "dataset"); doc.setField("title", "title " + id);
      for (int i=0;i<distributions;i++) {
        SolrInputDocument child = new SolrInputDocument(); child.setField("id", id+"-dist-"+i);
        child.setField("nodeID", node); child.setField("content_type", "distribution");
        SolrInputDocument detail = new SolrInputDocument(); detail.setField("id", id+"-detail-"+i);
        detail.setField("nodeID", node); detail.setField("content_type", "distributionDetails");
        child.addChildDocument(detail); doc.addChildDocument(child);
      }
    }
    @Override public SolrInputDocument toDoc() { return doc; }
  }
  long count(String query) throws Exception { return SolrSyncBenchmarkTest.count(server, query); }
  @Test void addReplaceDeleteAndLookupBeforeVisibility() throws Exception {
    Fixture first = new Fixture("96", "one", 12);
    try (SyncIndexBatch batch = new SyncIndexBatch(server,96,List.of(),1000)) {
      batch.upsert(first); assertSame(first,batch.find("one")); assertEquals(0,count("*: *".replace(" ","")));
      batch.flush(); assertEquals(25,count("_root_:one"));
      Fixture changed = new Fixture("96","one",3); batch.upsert(changed);
      assertSame(changed,batch.find("one")); batch.flush(); assertEquals(7,count("_root_:one"));
      batch.delete(changed); assertThrows(DatasetNotFoundException.class,()->batch.find("one"));
    }
    assertEquals(0,count("*: *".replace(" ","")));
  }
  @Test void thresholdFinalAndZeroChange() throws Exception {
    SyncIndexBatch batch = new SyncIndexBatch(server,96,List.of(),5);
    batch.upsert(new Fixture("96","many",20)); assertEquals(41,count("_root_:many"));
    batch.upsert(new Fixture("96","last",0)); assertEquals(0,count("id:last"));
    batch.close(); assertEquals(1,count("id:last"));
    assertTrue(batch.metrics().contains("commits=2"));
    assertThrows(IllegalStateException.class,()->batch.find("last"));
    try (SyncIndexBatch empty = new SyncIndexBatch(server,96,List.of(),5)) { empty.flush(); assertTrue(empty.metrics().contains("commits=0")); }
  }
  @Test void coalescesSameIdAndPreservesForeignCatalogue() throws Exception {
    server.add(new Fixture("118","foreign",7).toDoc()); server.commit();
    try (SyncIndexBatch batch = new SyncIndexBatch(server,96,List.of(),1000)) {
      batch.upsert(new Fixture("96","one",20)); batch.upsert(new Fixture("96","one",1));
      assertThrows(IllegalArgumentException.class,()->batch.upsert(new Fixture("118","wrong",1)));
    }
    assertEquals(3,count("_root_:one")); assertEquals(15,count("_root_:foreign"));
  }
  @Test void twoConcurrentContextsAndSharedCommit() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Void>> calls = new ArrayList<>();
      for (int node : new int[]{96,118}) calls.add(()->{
        try (SyncIndexBatch batch = new SyncIndexBatch(server,node,List.of(),20)) {
          for (int i=0;i<10;i++) batch.upsert(new Fixture(""+node,node+"-"+i,4));
        } return null;
      });
      for (Future<Void> result : executor.invokeAll(calls)) result.get();
    } finally { executor.shutdownNow(); }
    assertEquals(10,count("content_type:dataset AND nodeID:96"));
    assertEquals(10,count("content_type:dataset AND nodeID:118"));
    assertEquals(180,count("*: *".replace(" ","")));
  }
  static class FaultClient extends SolrClient {
    final SolrClient delegate; String fault; boolean persistent;
    FaultClient(SolrClient delegate,String fault) { this.delegate=delegate; this.fault=fault; }
    @Override public NamedList<Object> request(SolrRequest request,String collection) throws IOException,SolrServerException {
      boolean match = fault != null && request instanceof UpdateRequest && ("commit".equals(fault)
          ? request.getParams() != null && request.getParams().getBool("commit",false)
          : ((UpdateRequest)request).getDocuments()!=null);
      if (match) { if (!persistent) fault=null; throw new IOException("Injected index failure"); }
      return delegate.request(request,collection);
    }
    @Override public void close() {}
  }
  @Test void addFailureRetainsSetAndReplaysOnlyItsTrees() throws Exception { recover("add"); }
  @Test void commitFailureRetainsSetAndReplaysOnlyItsTrees() throws Exception { recover("commit"); }
  void recover(String fault) throws Exception {
    server.add(new Fixture("118","foreign",2).toDoc()); server.commit();
    FaultClient client=new FaultClient(server,fault);
    try (SyncIndexBatch batch = new SyncIndexBatch(client,96,List.of(),1000)) {
      Fixture dataset=new Fixture("96","one",8); batch.upsert(dataset);
      assertThrows(IOException.class,batch::flush); assertSame(dataset,batch.find("one"));
      batch.flush(); assertEquals(17,count("_root_:one"));
    }
    assertEquals(5,count("_root_:foreign"));
  }
  @Test void persistentFailurePropagatesAndClosesContext() throws Exception {
    FaultClient client = new FaultClient(server,"add"); client.persistent=true;
    SyncIndexBatch batch = new SyncIndexBatch(client,96,List.of(),1000);
    batch.upsert(new Fixture("96","one",1)); assertThrows(IOException.class,batch::close);
    assertThrows(IllegalStateException.class,batch::flush);
    // Reconcile the persisted dataset using a fresh context; never global rollback.
    try (SyncIndexBatch retry = new SyncIndexBatch(server,96,List.of(),1000)) { retry.upsert(new Fixture("96","one",1)); }
    assertEquals(3,count("_root_:one"));
  }
  @Test void preparationFailureRetainsPersistedIdForRecovery() throws Exception {
    Fixture dataset = new Fixture("96","one",4) {
      boolean first = true;
      @Override public SolrInputDocument toDoc() {
        if (first) { first=false; throw new IllegalStateException("Injected preparation failure"); }
        return super.toDoc();
      }
    };
    try (SyncIndexBatch batch = new SyncIndexBatch(server,96,List.of(),1000)) {
      assertThrows(IllegalStateException.class,()->batch.upsert(dataset));
      assertSame(dataset,batch.find("one")); batch.flush();
    }
    assertEquals(9,count("_root_:one"));
  }
  @Test void immediateCallerCanPublishWhileBatchRemainsPending() throws Exception {
    try (SyncIndexBatch batch = new SyncIndexBatch(server,96,List.of(),1000)) {
      batch.upsert(new Fixture("96","one",1));
      server.add(new Fixture("118","immediate",1).toDoc()); server.commit();
      assertEquals(3,count("_root_:immediate")); assertEquals(0,count("_root_:one"));
    }
    assertEquals(3,count("_root_:one"));
  }
}
