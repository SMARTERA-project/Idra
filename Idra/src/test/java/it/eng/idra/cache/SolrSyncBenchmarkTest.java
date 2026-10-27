package it.eng.idra.cache;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.dcat.dump.DcatApDeserializer;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in fixed-corpus benchmark. No application DB, network, or production index. */
class SolrSyncBenchmarkTest {
  @TempDir Path temp;
  static EmbeddedSolrServer open(Path home) throws Exception {
    Path source = Path.of("src/main/webapp/WEB-INF/classes/solr");
    Files.createDirectories(home.resolve("core"));
    Files.copy(source.resolve("solr.xml"), home.resolve("solr.xml"));
    Files.copy(source.resolve("core.properties"), home.resolve("core/core.properties"));
    try (var paths = Files.walk(source.resolve("conf"))) {
      for (Path p : paths.toList()) {
        Path dest = home.resolve("core/conf").resolve(source.resolve("conf").relativize(p));
        if (Files.isDirectory(p)) Files.createDirectories(dest); else Files.copy(p, dest);
      }
    }
    return new EmbeddedSolrServer(CoreContainer.createAndLoad(home), "core") {
      @Override public void close() throws java.io.IOException {
        try { super.close(); } finally { getCoreContainer().shutdown(); }
      }
    };
  }
  static long count(SolrClient client, String query) throws Exception {
    return client.query(new SolrQuery(query).setRows(0)).getResults().getNumFound();
  }
  static void assignFixtureIds(SolrInputDocument doc, String root, int[] sequence) {
    if (doc.getFieldValue("id") == null) doc.setField("id", root + "-child-" + sequence[0]++);
    if (doc.hasChildDocuments()) for (var child : doc.getChildDocuments()) assignFixtureIds(child, root, sequence);
  }
  static Map<String,Long> counters(EmbeddedSolrServer client) {
    Map<String,Long> result = new TreeMap<>();
    try (var core = client.getCoreContainer().getCore("core")) {
      core.getMetricRegistry().getMetrics().forEach((name,metric)->{
        if (!name.startsWith("UPDATE.") || !(name.toLowerCase(Locale.ROOT).contains("commit") || name.endsWith("/update.requests"))) return;
        if (metric instanceof com.codahale.metrics.Counter) result.put(name,((com.codahale.metrics.Counter)metric).getCount());
        else if (metric instanceof com.codahale.metrics.Timer) result.put(name,((com.codahale.metrics.Timer)metric).getCount());
        else if (metric instanceof com.codahale.metrics.Gauge) {
          Object value = ((com.codahale.metrics.Gauge<?>)metric).getValue();
          if (value instanceof Number) result.put(name,((Number)value).longValue());
        }
      });
    }
    return result;
  }
  static Map<String,Long> delta(EmbeddedSolrServer client,Map<String,Long> before) {
    Map<String,Long> result=counters(client); result.replaceAll((key,value)->value-before.getOrDefault(key,0L)); return result;
  }
  @Test void fixedParisCorpus() throws Exception {
    Assumptions.assumeTrue(System.getProperty("plan46.fixture") != null);
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, System.getProperty("plan46.fixture"), Lang.RDFXML);
    var resources = model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,
        model.createResource("http://www.w3.org/ns/dcat#Dataset")).toList();
    resources.sort(Comparator.comparing(Resource::getURI));
    List<SolrInputDocument> docs = new ArrayList<>();
    long prepare = System.nanoTime(); int distributionCount = 0;
    for (Resource resource : resources) {
      DcatDataset dataset = new DcatApDeserializer().resourceToDataset("96", resource);
      dataset.setId(it.eng.idra.utils.CommonUtil.extractSeoIdentifier(dataset.getTitle().getValue(),
          UUID.nameUUIDFromBytes(resource.getURI().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"96"));
      int child = 0;
      for (var distribution : dataset.getDistributions()) {
        distribution.setId(dataset.getId() + "-dist-" + child++);
      }
      distributionCount += child;
      SolrInputDocument doc = dataset.toDoc();
      assignFixtureIds(doc, dataset.getId(), new int[]{0});
      docs.add(doc);
    }
    long preparationNs = System.nanoTime() - prepare;
    assertEquals(490, docs.size()); assertEquals(5850, distributionCount);
    String mode = System.getProperty("plan46.mode", "legacy");
    boolean[] modes = "compare".equals(mode) ? new boolean[]{false,true} : new boolean[]{"batch".equals(mode)};
    for (boolean batching : modes) for (int repetition = 0; repetition < Integer.getInteger("plan46.repetitions", 3); repetition++) {
      try (EmbeddedSolrServer client = open(temp.resolve((batching ? "batch-" : "legacy-") + repetition))) {
        for (boolean refresh : new boolean[]{false, true}) {
          Map<String,Long> before=counters(client);
          if (batching) {
            long start = System.nanoTime(); long heap = 0;
            try (SyncIndexBatch batch = new SyncIndexBatch(client,96,List.of(),1000)) {
              for (SolrInputDocument doc : docs) {
                // Both paths consume the exact same prepared document trees and IDs.
                DcatDataset dataset = new DcatDataset() {
                  { setIdentifier(new it.eng.idra.beans.dcat.DcatProperty(
                      org.apache.jena.vocabulary.DCTerms.identifier, org.apache.jena.vocabulary.RDFS.Literal,
                      doc.getFieldValue("identifier").toString())); }
                  @Override public SolrInputDocument toDoc() { return doc; }
                };
                dataset.setId(doc.getFieldValue("id").toString()); dataset.setNodeId("96");
                batch.upsert(dataset);
                heap = Math.max(heap, Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
              }
              batch.flush();
              long total = System.nanoTime()-start;
              assertEquals(490, count(client,"content_type:dataset AND nodeID:96"));
              assertEquals(5850, count(client,"content_type:distribution AND nodeID:96"));
              System.out.printf(Locale.ROOT,"PLAN46 batch repetition=%d refresh=%s totalMs=%.3f commonPrepareMs=%.3f sampledHeapMiB=%.3f allDocs=%d %s%n",
                  repetition,refresh,total/1e6,preparationNs/1e6,heap/1048576.0,count(client,"*:*"),batch.metrics());
              System.out.println("PLAN46 server batch repetition="+repetition+" refresh="+refresh+" counters="+delta(client,before));
            }
            continue;
          }
          long addNs = 0, deleteNs = 0, commitNs = 0, start = System.nanoTime();
          long heap = 0;
          for (SolrInputDocument doc : docs) {
            long t = System.nanoTime();
            if (refresh) client.deleteByQuery("_root_:\"" + doc.getFieldValue("id") + "\" AND nodeID:96");
            deleteNs += System.nanoTime() - t; t = System.nanoTime();
            client.add(doc); addNs += System.nanoTime() - t; t = System.nanoTime();
            client.commit(); commitNs += System.nanoTime() - t;
            heap = Math.max(heap, Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
          }
          long total = System.nanoTime() - start;
          assertEquals(490, count(client, "content_type:dataset AND nodeID:96"));
          assertEquals(5850, count(client, "content_type:distribution AND nodeID:96"));
          System.out.printf(Locale.ROOT, "PLAN46 legacy repetition=%d refresh=%s datasets=490 distributions=5850 commits=490 requests=%d prepareMs=%.3f deleteMs=%.3f addMs=%.3f commitMs=%.3f totalMs=%.3f allDocs=%d sampledHeapMiB=%.3f%n",
              repetition, refresh, refresh ? 1470 : 980, preparationNs/1e6, deleteNs/1e6, addNs/1e6, commitNs/1e6, total/1e6, count(client,"*:*"),heap/1048576.0);
          System.out.println("PLAN46 server legacy repetition="+repetition+" refresh="+refresh+" counters="+delta(client,before));
        }
      }
    }
  }
}
