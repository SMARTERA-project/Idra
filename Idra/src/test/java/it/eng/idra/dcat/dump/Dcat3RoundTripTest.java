package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.utils.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Dcat3RoundTripTest {
  private static final String EX="https://example.org/";
  private DcatDataset parse(DcatApDeserializer parser) throws Exception {
    Model model=ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, getClass().getResourceAsStream("/dcat3/series.ttl"), Lang.TURTLE);
    return parser.resourceToDataset("999999", model.getResource(EX+"dataset/v2"));
  }
  @Test void preservesSeriesAndVersionIdentityInBothProfiles() throws Exception {
    for (DcatApDeserializer parser : Arrays.asList(new DcatApDeserializer(), new DcatApItDeserializer())) {
      DcatDataset dataset=parse(parser);
      assertEquals(EX+"dataset/v2",dataset.getResourceUri());
      assertEquals("2.0",dataset.getVersion().getValue());
      assertEquals(Arrays.asList(EX+"dataset/v1"), dataset.getPreviousVersion());
      assertEquals(1,dataset.getInSeries().size());
      assertEquals(EX+"series",dataset.getInSeries().get(0).getResourceUri());
      Model output=ModelFactory.createDefaultModel();
      DcatApSerializer.nodeResources = new HashMap<>();
      DcatApSerializer.addDatasetToModel(dataset,output);
      Resource subject=output.getResource(EX+"dataset/v2");
      assertTrue(subject.hasProperty(ResourceFactory.createProperty(DCAT.NS+"inSeries"),output.getResource(EX+"series")));
      assertTrue(subject.hasProperty(ResourceFactory.createProperty(DCAT.NS+"version"),"2.0"));
      assertTrue(output.getResource(EX+"series").hasProperty(DCTerms.title));
      DcatDataset reread=parser.resourceToDataset("999999",subject);
      assertEquals(dataset.getPreviousVersion(),reread.getPreviousVersion());
      assertEquals(dataset.getInSeries().get(0).getResourceUri(),reread.getInSeries().get(0).getResourceUri());
    }
  }
  @Test void v2PropertiesAndHvdAreNotV3Evidence() {
    DcatDataset dataset=new DcatDataset();
    dataset.setTemporalResolution(new DcatProperty(DCAT.temporalResolution,XSD.duration,"PT1H"));
    dataset.setHVDCategory(Arrays.asList(new DcatProperty(DCATAP.hvdCategory,RDFS.Resource,EX+"hvd")));
    assertNull(DcatVersionDetector.detect(Arrays.asList(dataset),DcatApVersion.DCAT_AP_3));
    dataset.setPreviousVersion(Arrays.asList(EX+"v1"));
    assertEquals(DcatApVersion.DCAT_AP_3,DcatVersionDetector.detect(Arrays.asList(dataset),null));
  }
  @Test void acceptsNormalizedAndNativeExtensionValues() {
    DcatDataset dataset=new DcatDataset(); dataset.setNodeId("fixture");dataset.setId("id");
    DcatJsonMetadata.apply(dataset,new JSONObject("{\"hvdCategory\":{\"type\":\"Property\",\"value\":[\"https://example.org/hvd\"]},\"previousVersion\":{\"type\":\"Relationship\",\"object\":\"https://example.org/v1\"},\"inSeries\":[{\"resourceUri\":\"https://example.org/series\",\"title\":\"Serie\"}]}"));
    assertEquals(EX+"hvd",dataset.getHVDCategory().get(0).getValue());
    assertEquals(Arrays.asList(EX+"v1"),dataset.getPreviousVersion());
    assertEquals(EX+"series",dataset.getInSeries().get(0).getResourceUri());
  }
  @Test void readsGeneratedBrokerFixtureWithoutDroppingMetadata() throws Exception {
    String path=System.getProperty("ngsi.fixture");
    org.junit.jupiter.api.Assumptions.assumeTrue(path!=null, "Pass -Dngsi.fixture for cross-module contract test");
    JSONArray entities=new JSONArray(java.nio.file.Files.readString(java.nio.file.Path.of(path)));
    Map<String,JSONObject> byId=new HashMap<>();
    for(int i=0;i<entities.length();i++)byId.put(entities.getJSONObject(i).getString("id"),entities.getJSONObject(i));
    JSONObject entity=byId.get("urn:ngsi-ld:Dataset:id:fixture-dataset");
    DcatDataset dataset=NgsiDcatReader.dataset(entity,"987654",byId::get);
    assertEquals("P1Y",dataset.getTemporalResolution().getValue());
    assertEquals(EX+"v1",dataset.getPreviousVersion().get(0));
    assertEquals(EX+"series",dataset.getInSeries().get(0).getResourceUri());
    assertEquals(2,dataset.getDatasetDetails().size());
    assertEquals(EX+"hvd",dataset.getHVDCategory().get(0).getValue());
    assertEquals("application/gzip",dataset.getDistributions().get(0).getCompressionFormat().getValue());
    assertEquals(EX+"api",dataset.getDistributions().get(0).getAccessService().get(0).getEndpointURL().get(0).getValue());
  }
  @Test void officialShapesDistinguishSourceViolationsFromRoundTrip() throws Exception {
    Model shapes=ModelFactory.createDefaultModel();
    RDFDataMgr.read(shapes,getClass().getResourceAsStream("/dcat3/shacl/shapes.ttl"),Lang.TURTLE);
    Model source=ModelFactory.createDefaultModel();
    RDFDataMgr.read(source,getClass().getResourceAsStream("/dcat3/series.ttl"),Lang.TURTLE);
    org.apache.jena.shacl.ValidationReport report=org.apache.jena.shacl.ShaclValidator.get().validate(shapes.getGraph(),source.getGraph());
    assertTrue(report.conforms(),report.getModel().toString());
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",source.getResource(EX+"dataset/v2"));
    Model output=ModelFactory.createDefaultModel();DcatApSerializer.nodeResources=new HashMap<>();
    DcatApSerializer.addDatasetToModel(dataset,output);
    org.apache.jena.shacl.ValidationReport roundTrip=org.apache.jena.shacl.ShaclValidator.get().validate(shapes.getGraph(),output.getGraph());
    assertTrue(roundTrip.conforms(),roundTrip.getModel().toString());
  }
}
