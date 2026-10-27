package it.eng.idra.cache;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.dcat.dump.DcatApDeserializer;
import it.eng.idra.dcat.dump.DcatApSerializer;
import it.eng.idra.beans.dcat.DcatApProfile;
import java.nio.file.Path;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DcatMetadataSolrTest {
  @TempDir Path temp;
  @Test void envelopeSurvivesRealSolrStorageAndTypedReload() throws Exception {
    Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,getClass().getResourceAsStream("/dcat48/structured.ttl"),Lang.TURTLE);
    DcatDataset d=new DcatApDeserializer().resourceToDataset("999999",source.getResource("https://example.org/plan48/dataset"));
    SolrInputDocument input=d.toDoc();SolrSyncBenchmarkTest.assignFixtureIds(input,d.getId(),new int[]{0});
    try(var server=SolrSyncBenchmarkTest.open(temp.resolve("solr"))) {
      server.add(input);server.commit();
      SolrQuery query=new SolrQuery("content_type:dataset").setFields("*,[child parentFilter=content_type:dataset limit=1000]");
      SolrDocument stored=server.query(query).getResults().get(0);DcatDataset loaded=DcatDataset.docToDataset(stored);
      assertEquals(d.getMetadataEnvelope(),loaded.getMetadataEnvelope());
      try(var core=server.getCoreContainer().getCore("core")) {
        assertFalse(core.getLatestSchema().getField("metadataEnvelope").indexed(),"Evidence is stored, not indexed");
      }
    }
  }
  @Test void datasetWithManyDistributionsReloadsCompletely() throws Exception {
    StringBuilder ttl=new StringBuilder("@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>.\n<https://example.org/plan62/many> a dcat:Dataset; dct:identifier \"many\"; dct:title \"Many distributions\"@it");
    int total=900;
    for(int i=0;i<total;i++)ttl.append("; dcat:distribution <https://example.org/plan62/many/d").append(i).append(">");
    ttl.append(".\n");
    for(int i=0;i<total;i++)ttl.append("<https://example.org/plan62/many/d").append(i).append("> a dcat:Distribution; dct:title \"d").append(i).append("\"; dcat:accessURL <https://example.org/files/").append(i).append(".csv>; dct:format \"CSV\"; dct:license <http://creativecommons.org/licenses/by/4.0/>.\n");
    Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,new java.io.ByteArrayInputStream(ttl.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),Lang.TURTLE);
    DcatDataset d=new DcatApDeserializer().resourceToDataset("999997",source.getResource("https://example.org/plan62/many"));
    assertEquals(total,d.getDistributions().size());
    SolrInputDocument input=d.toDoc();SolrSyncBenchmarkTest.assignFixtureIds(input,d.getId(),new int[]{0});
    try(var server=SolrSyncBenchmarkTest.open(temp.resolve("solr-many"))) {
      server.add(input);server.commit();
      SolrQuery truncated=new SolrQuery("content_type:dataset").setFields("*,[child parentFilter=content_type:dataset limit=1000]");
      assertTrue(DcatDataset.docToDataset(server.query(truncated).getResults().get(0)).getDistributions().size()<total,"Former fixed limit reproduces the truncation");
      SolrQuery query=new SolrQuery("content_type:dataset").setFields("*,"+MetadataCacheManager.ALL_CHILDREN.replace("$parent_filter","content_type:dataset"));
      assertEquals(total,DcatDataset.docToDataset(server.query(query).getResults().get(0)).getDistributions().size());
    }
  }
  @Test void unlabelledSubjectAndThemeReloadFromSolr() throws Exception {
    String ttl="@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>.\n"
      +"<https://example.org/plan62/unlabelled> a dcat:Dataset; dct:identifier \"unlabelled\"; dct:title \"Unlabelled concepts\"@it;"
      +" dct:subject <https://example.org/plan62/subject-without-label>; dcat:theme <https://example.org/plan62/theme-without-label>.";
    Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,new java.io.ByteArrayInputStream(ttl.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Lang.TURTLE);
    DcatDataset d=new DcatApDeserializer().resourceToDataset("999998",source.getResource("https://example.org/plan62/unlabelled"));
    SolrInputDocument input=d.toDoc();SolrSyncBenchmarkTest.assignFixtureIds(input,d.getId(),new int[]{0});
    try(var server=SolrSyncBenchmarkTest.open(temp.resolve("solr-unlabelled"))) {
      server.add(input);server.commit();
      SolrQuery query=new SolrQuery("content_type:dataset").setFields("*,[child parentFilter=content_type:dataset limit=1000]");
      DcatDataset loaded=DcatDataset.docToDataset(server.query(query).getResults().get(0));
      assertEquals("https://example.org/plan62/subject-without-label",loaded.getSubject().get(0).getResourceUri());
      assertTrue(loaded.getSubject().get(0).getPrefLabel().isEmpty(),"No label is invented on reload");
      assertTrue(loaded.getTheme().stream().anyMatch(t->"https://example.org/plan62/theme-without-label".equals(t.getResourceUri())));
    }
  }
}
