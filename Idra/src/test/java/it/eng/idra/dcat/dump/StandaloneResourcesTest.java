package it.eng.idra.dcat.dump;

import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.utils.NgsiDcatReader;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneResourcesTest {
  private OdmsCatalogue node() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("https://example.org/catalog");
    return node;
  }
  @Test void standaloneSeriesHasNoDatasetOwnerAndKeepsTheExactGraph() {
    Model source=ModelFactory.createDefaultModel();
    source.createResource("https://example.org/catalog",DCAT.Catalog).addProperty(DCTerms.title,"Catalogue");
    source.createResource("https://example.org/series",source.createResource(DCAT.NS+"DatasetSeries"))
        .addProperty(DCTerms.title,source.createLiteral("Serie autonoma","it"))
        .addProperty(DCTerms.description,source.createLiteral("東京","ja"))
        .addProperty(source.createProperty("https://example.org/custom"),source.createResource().addProperty(RDFS.label,"Evidence"));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(source,node);
    assertEquals(1,node.getDatasetSeries().size());
    assertEquals("Serie autonoma",node.getDatasetSeries().get(0).getTitle().get(0).getTitle());
    assertNull(node.getDatasetSeries().get(0).getDatasetId());
    assertEquals("999999",node.getDatasetSeries().get(0).getNodeId());
    Model exported=ModelFactory.createDefaultModel();DcatApSerializer.addStandaloneCatalogueToModel(node,exported);
    assertTrue(source.isIsomorphicWith(exported));
    assertFalse(exported.contains(null,RDF.type,DCAT.Dataset));
    assertFalse(exported.contains(null,DCAT.dataset));
  }
  @Test void serviceWithoutAnExplicitCatalogueRemainsAService() {
    Model source=ModelFactory.createDefaultModel();
    source.createResource("https://example.org/service",DCAT.DataService)
        .addProperty(DCTerms.title,source.createLiteral("API città","it"))
        .addProperty(DCAT.endpointURL,source.createResource("https://example.org/api"));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(source,node);
    assertEquals(1,node.getService().size());assertTrue(node.getDatasetSeries().isEmpty());
    assertEquals("https://example.org/service",node.getService().get(0).getResourceUri());
    Model exported=ModelFactory.createDefaultModel();DcatApSerializer.addStandaloneCatalogueToModel(node,exported);
    assertTrue(source.isIsomorphicWith(exported));
  }
  @Test void normalizedCatalogueEvidenceReconstructsUnknownPropertiesAndLanguageTags() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    source.createResource("https://example.org/service",DCAT.DataService)
        .addProperty(source.createProperty("https://example.org/custom"),source.createLiteral("Město","cs-t-en-t0-mtec"));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(source,node);
    String evidence=new JSONObject().put("version",1).put("sourceRdf",node.getStandaloneRdf()).toString();
    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
    try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(bytes)) {gzip.write(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    JSONObject transport=new JSONObject().put("encoding","gzip-base64-utf8-chunks-v1")
        .put("chunks",new org.json.JSONArray().put(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())));
    JSONObject entity=new JSONObject().put("id","urn:catalogue:test").put("type","CatalogueDCAT-AP")
        .put("metadataEnvelope",new JSONObject().put("type","Property").put("value",transport));
    assertTrue(source.isIsomorphicWith(NgsiDcatReader.standaloneCatalogueGraph(entity)));
  }
  @Test void independentSeriesAreAlsoWrittenForCataloguesContainingDatasets() {
    Model source=ModelFactory.createDefaultModel();
    source.createResource("https://example.org/catalog",DCAT.Catalog);
    source.createResource("https://example.org/dataset",DCAT.Dataset).addProperty(DCTerms.title,"Dataset");
    source.createResource("https://example.org/series",source.createResource(DCAT.NS+"DatasetSeries"))
        .addProperty(DCTerms.title,source.createLiteral("Serie autonoma","it"));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(source,node);
    assertNull(node.getStandaloneRdf());
    Model out=ModelFactory.createDefaultModel();DcatRdfMetadata.writeCatalogue(node,out.createResource(node.getHost(),DCAT.Catalog));
    assertTrue(out.getResource("https://example.org/series").hasProperty(DCTerms.title,out.createLiteral("Serie autonoma","it")));
    assertFalse(out.contains(null,DCAT.dataset));
  }
  @Test void publicCatalogueJsonContainsDcatMetadataButKeepsTechnicalFieldsPrivate() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    source.createResource("https://example.org/series",source.createResource(DCAT.NS+"DatasetSeries")).addProperty(DCTerms.title,source.createLiteral("Serie autonoma","it"));
    source.createResource("https://example.org/service",DCAT.DataService).addProperty(DCTerms.title,"API").addProperty(DCAT.endpointURL,source.createResource("https://example.org/api"));
    OdmsCatalogue node=node();node.setApiKey("private-fixture");node.setDumpFilePath("private-path");
    new DcatApDeserializer().readCatalogue(source,node);
    JSONObject json=new JSONObject(it.eng.idra.utils.GsonUtil.obj2JsonWithExclude(node,it.eng.idra.utils.GsonUtil.nodeType));
    assertFalse(json.has("APIKey"));assertFalse(json.has("dumpFilePath"));
    assertEquals("https://example.org/series",json.getJSONArray("datasetSeries").getJSONObject(0).getString("resourceUri"));
    assertEquals("Serie autonoma",json.getJSONArray("datasetSeries").getJSONObject(0).getJSONArray("title").getJSONObject(0).getString("title"));
    assertEquals("https://example.org/service",json.getJSONArray("service").getJSONObject(0).getString("resourceUri"));
    assertEquals("https://example.org/api",json.getJSONArray("service").getJSONObject(0).getJSONArray("endpointURL").getString(0));
  }
  @Test void ambiguousCatalogueGraphsAreRejectedInsteadOfCombiningUnrelatedRoots() {
    Model source=ModelFactory.createDefaultModel();source.createResource("https://elsewhere.org/a",DCAT.Catalog);
    source.createResource("https://elsewhere.org/b",DCAT.Catalog);
    source.createResource("https://elsewhere.org/service",DCAT.DataService);
    assertThrows(IllegalArgumentException.class,()->new DcatApDeserializer().readCatalogue(source,node()));
  }
}
