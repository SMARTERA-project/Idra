package it.eng.idra.dcat.dump;

import com.google.gson.Gson;
import it.eng.idra.beans.dcat.*;
import it.eng.idra.utils.*;
import java.nio.file.*;
import java.util.*;
import javax.persistence.Entity;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.apache.solr.common.*;
import org.hibernate.*;
import org.hibernate.cfg.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatMetadataEnvelopeTest {
  static final String EX="https://example.org/plan48/";
  static DcatDataset fixture() throws Exception {
    Model m=ModelFactory.createDefaultModel();RDFDataMgr.read(m,DcatMetadataEnvelopeTest.class.getResourceAsStream("/dcat48/structured.ttl"),Lang.TURTLE);
    return new DcatApDeserializer().resourceToDataset("999999",m.getResource(EX+"dataset"));
  }
  static Model output(DcatDataset d,boolean italian) {
    DcatApSerializer.nodeResources=new HashMap<>();Model m=ModelFactory.createDefaultModel();
    return italian?DcatApItSerializer.addDatasetToModel(d,m):DcatApSerializer.addDatasetToModel(d,m);
  }
  static void assertRich(Model m) {
    Resource root=m.getResource(EX+"dataset"),dist=m.getResource(EX+"distribution");
    assertEquals(2,root.listProperties(m.createProperty(EX+"multi")).toList().size());
    assertEquals(2,root.listProperties(DCTerms.publisher).toList().size());
    assertTrue(root.hasProperty(m.createProperty("https://other.example/property"),"namespace preserved"));
    Resource annotation=root.getPropertyResourceValue(m.createProperty(EX+"annotation"));
    assertTrue(annotation.hasProperty(RDF.type,m.createResource(EX+"Measurement")));
    Literal number=annotation.getProperty(m.createProperty(EX+"value")).getLiteral();
    assertEquals("01.250",number.getLexicalForm());assertEquals(XSD.decimal.getURI(),number.getDatatypeURI());
    Resource contact=root.getPropertyResourceValue(DCAT.contactPoint);
    assertTrue(contact.hasProperty(m.createProperty(EX+"role"),m.createResource(EX+"curator")));
    Resource licence=dist.getPropertyResourceValue(DCTerms.license);
    assertTrue(licence.hasProperty(m.createProperty(EX+"restriction"),"retain attribution"));
    assertTrue(root.hasProperty(DCTerms.language,m.createResource("http://publications.europa.eu/resource/authority/language/FRA")));
    assertTrue(root.hasProperty(DCTerms.accessRights,"otherRestrictions"));
    assertTrue(dist.hasProperty(DCTerms.format,m.createResource(DcatApSerializer.FORMAT_BASE_URI+"CSV")));
    assertTrue(dist.hasProperty(DCAT.mediaType,m.createResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
  }
  @Test void preservesStructuredSourceAndExplicitMappingsInBothProfiles() throws Exception {
    DcatDataset d=fixture();String source=d.getMetadataEnvelope();
    JSONObject envelope=new JSONObject(source);assertEquals(1,envelope.getInt("version"));
    JSONArray mappings=envelope.getJSONArray("mappings");
    assertTrue(mappings.toString().contains("otherRestrictions"));
    assertTrue(mappings.toString().contains("UNMAPPED"));assertTrue(mappings.toString().contains("sha256"));
    assertRich(output(d,false));assertRich(output(d,true));assertEquals(source,d.getMetadataEnvelope());
    assertEquals("csv",d.getDistributions().get(0).getFormat().getValue());
    d.setMetadataEnvelope(null);
    assertFalse(output(d,false).contains(null,ResourceFactory.createProperty(EX+"annotation")),"Legacy typed model demonstrably loses the extension");
  }
  @Test void typedEditsInvalidateTheirSourceBranchAndUnverifiedRulesAreRejected() throws Exception {
    DcatDataset d=fixture();d.getTitle().setValue("Edited");
    d.setDatasetDetails(DcatDetailsUtil.extractDatasetDetails("Edited","New description"));
    assertTrue(output(d,false).getResource(EX+"dataset").hasProperty(DCTerms.title,"Edited"));
    JSONObject envelope=new JSONObject(d.getMetadataEnvelope());
    for(Object value:envelope.getJSONArray("mappings")) {
      JSONObject entry=(JSONObject)value;if(entry.has("canonical")) {entry.put("canonical","https://malicious.example/invented");break;}
    }
    d.setMetadataEnvelope(envelope.toString());assertThrows(IllegalArgumentException.class,()->output(d,false));
  }
  @Test void localThemesKeepSourceLanguageAndCatalogueOwnership() {
    List<SkosConceptTheme> a=SkosConceptFactory.build(DCAT.theme.getURI(),Arrays.asList("Citoyenneté"),SkosConceptTheme.class,"96","fr");
    List<SkosConceptTheme> b=SkosConceptFactory.build(DCAT.theme.getURI(),Arrays.asList("Citoyenneté"),SkosConceptTheme.class,"118","fr");
    assertTrue(a.get(0).getResourceUri().startsWith("urn:idra:concept:96:"));
    assertNotEquals(a.get(0).getResourceUri(),b.get(0).getResourceUri());
    assertEquals("Citoyenneté",a.get(0).getPrefLabel().get(0).getValue());
    assertEquals("fr",a.get(0).getPrefLabel().get(0).getLanguage());
    assertFalse(a.get(0).getResourceUri().contains("data-theme"));
  }
  @Test void nonRdfMappingsDoNotInferProtocolOrChangeUnknownIris() throws Exception {
    DcatDataset d=fixture();d.setMetadataEnvelope(null);
    DcatDistribution dist=d.getDistributions().get(0);
    dist.setFormat("urn:ogc:def:serviceType:OGC:WFS");dist.setMediaType("not-a-media-type");
    DcatMetadataEnvelope.captureTyped(d,"OPENDATASOFT:999999");
    Model m=output(d,false);Resource r=m.getResource(EX+"distribution");
    assertTrue(r.hasProperty(DCTerms.format,m.createResource("urn:ogc:def:serviceType:OGC:WFS")));
    assertFalse(m.contains(m.createResource("urn:ogc:def:serviceType:OGC:WFS"),RDF.type,DCTerms.MediaTypeOrExtent));
    assertFalse(m.contains(null,DCAT.mediaType,m.createResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
    assertTrue(d.getMetadataEnvelope().contains("UNMAPPED"));
  }
  @Test void nativeMetadataTransferKeepsOriginalEvidenceAndLocalConceptOwnership() throws Exception {
    DcatDataset d=fixture();String evidence=d.getMetadataEnvelope();
    DcatDataset copy=fixture();DcatJsonMetadata.apply(copy,new JSONObject().put("metadataEnvelope",evidence));
    assertEquals(evidence,copy.getMetadataEnvelope());
    Resource before=output(copy,false).getResource(EX+"dataset").getPropertyResourceValue(DCAT.theme);
    copy.setNodeId("999998");
    Resource after=output(copy,false).getResource(EX+"dataset").getPropertyResourceValue(DCAT.theme);
    assertEquals(before.getURI(),after.getURI());
  }
  @Test void blankDistributionKeepsItsMetadataWithoutAnOrphanSyntheticDuplicate() throws Exception {
    String text=new String(getClass().getResourceAsStream("/dcat48/structured.ttl").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
        .replace("ex:distribution","_:distribution");
    Model source=ModelFactory.createDefaultModel();RDFParser.fromString(text).lang(Lang.TURTLE).parse(source);
    DcatDataset d=new DcatApDeserializer().resourceToDataset("999999",source.getResource(EX+"dataset"));
    Model result=output(d,false);Resource distribution=result.getResource(EX+"dataset").getPropertyResourceValue(DCAT.distribution);
    assertTrue(distribution.isAnon());assertEquals(1,result.listResourcesWithProperty(RDF.type,DCAT.Distribution).toList().size());
    assertTrue(distribution.hasProperty(DCTerms.format,result.createResource(DcatApSerializer.FORMAT_BASE_URI+"CSV")));
    assertTrue(distribution.hasProperty(DCAT.mediaType,result.createResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
    assertTrue(distribution.getPropertyResourceValue(DCTerms.license).hasProperty(result.createProperty(EX+"restriction"),"retain attribution"));
  }
  @Test void persistsEnvelopeAndPassesStoredSolrProjection() throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:plan48;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");
    config.setProperty("hibernate.hbm2ddl.auto","create-drop");config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(java.util.stream.Stream<Path> files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    DcatDataset d=fixture();String evidence=d.getMetadataEnvelope();
    try(SessionFactory factory=config.buildSessionFactory()) {
      try(Session s=factory.openSession()){s.beginTransaction();s.persist(d);s.getTransaction().commit();}
      try(Session s=factory.openSession()) {
        DcatDataset loaded=s.get(DcatDataset.class,new DcatDatasetId(d.getId(),"999999"));
        assertEquals(evidence,loaded.getMetadataEnvelope());assertRich(output(loaded,false));
        SolrInputDocument doc=loaded.toDoc();assertEquals(evidence,doc.getFieldValue("metadataEnvelope"));
        // The production reader consumes stored fields; nested documents are tested in the existing Solr suite.
        SolrDocument stored=new SolrDocument();for(SolrInputField field:doc)stored.setField(field.getName(),field.getValue());
        assertEquals(evidence,DcatDataset.docToDataset(stored).getMetadataEnvelope());
        Files.writeString(Path.of("target/plan48-dataset.json"),new Gson().toJson(loaded));
      }
    }
  }
  @Test void bridgeReaderRetainsEnvelopeWhenRealBridgeFixtureIsProvided() throws Exception {
    String path=System.getProperty("plan48.bridge");if(path==null)return;
    JSONArray entities=new JSONArray(Files.readString(Path.of(path)));Map<String,JSONObject> map=new HashMap<>();
    JSONObject dataset=null;for(Object entry:entities){JSONObject e=(JSONObject)entry;map.put(e.getString("id"),e);if(e.getString("type").equals("Dataset"))dataset=e;}
    assertNotNull(dataset);DcatDataset d=NgsiDcatReader.dataset(dataset,"999999",map::get);
    assertNotNull(d.getMetadataEnvelope());assertRich(output(d,false));
  }
}
