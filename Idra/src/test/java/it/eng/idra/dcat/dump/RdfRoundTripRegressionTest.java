package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.utils.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RdfRoundTripRegressionTest {
  @Test void normalizedRecordReconstructsLocalizedTextAndProfileGraph() {
    JSONObject entity=new JSONObject().put("id","urn:test:record").put("type","CatalogueRecordDCAT-AP")
        .put("resourceUri",property("https://example.org/record"))
        .put("title",property(new JSONObject().put("it","Primo record")))
        .put("description",property(new JSONObject().put("it","Descrizione")))
        .put("conformsTo",property(new JSONArray().put(new JSONObject().put("uri","https://example.org/profile").put("identifier","p1").put("title","Profile"))))
        .put("language",property(new JSONArray().put("http://publications.europa.eu/resource/authority/language/ITA")))
        .put("dateCreated",property("2026-10-22")).put("primaryTopic",property("https://example.org/dataset"));
    DcatCatalogueRecord record=NgsiDcatReader.record(entity,"999999");
    it.eng.idra.beans.odms.OdmsCatalogue node=new it.eng.idra.beans.odms.OdmsCatalogue();node.setRecord(new ArrayList<>(List.of(record)));
    Model output=ModelFactory.createDefaultModel();DcatRdfMetadata.writeCatalogue(node,output.createResource("https://example.org/catalogue"));
    Resource r=output.getResource("https://example.org/record");
    assertTrue(r.hasProperty(DCTerms.title,output.createLiteral("Primo record","it")));
    assertTrue(r.hasProperty(DCTerms.description,output.createLiteral("Descrizione","it")));
    assertTrue(r.hasProperty(org.apache.jena.sparql.vocabulary.FOAF.primaryTopic,output.createResource("https://example.org/dataset")));
    assertTrue(output.getResource("https://example.org/profile").hasProperty(DCTerms.title,"Profile"));
    assertTrue(r.hasProperty(DCTerms.language,output.createResource("http://publications.europa.eu/resource/authority/language/ITA")));
    assertEquals("http://purl.org/dc/terms/issued",record.getListingDate().getProperty().getURI());
  }
  @Test void parsedRecordDetailsKeepTheirCatalogueOwner() {
    Model graph=ModelFactory.createDefaultModel();
    Resource record=graph.createResource("https://example.org/record",DCAT.CatalogRecord).addProperty(DCTerms.title,graph.createLiteral("Record italiano","it"));
    record.addProperty(DCTerms.issued,graph.createTypedLiteral("2026-10-22",org.apache.jena.datatypes.xsd.XSDDatatype.XSDdate));
    graph.createResource("https://example.org/catalogue",DCAT.Catalog).addProperty(DCAT.record,record);
    it.eng.idra.beans.odms.OdmsCatalogue node=new it.eng.idra.beans.odms.OdmsCatalogue();node.setId(999999);
    new DcatApDeserializer().readCatalogue(graph,node);
    assertEquals("999999",node.getRecord().get(0).getTitle().get(0).getNodeId());
    assertEquals("2026-10-22",node.getRecord().get(0).getListingDate().getValue());
    DctStandard profile=new DctStandard("https://example.org/profile","profile-id","Profile title","Description",List.of(),"999999");
    node.getRecord().get(0).setApplicationProfile(new ArrayList<>(List.of(profile)));
    Model exported=ModelFactory.createDefaultModel();DcatRdfMetadata.writeCatalogue(node,exported.createResource("https://example.org/catalogue"));
    assertTrue(exported.getResource("https://example.org/record").hasProperty(DCTerms.conformsTo,exported.createResource("https://example.org/profile")));
    assertTrue(exported.getResource("https://example.org/profile").hasProperty(DCTerms.title,"Profile title"));
    assertTrue(exported.getResource("https://example.org/profile").hasProperty(DCTerms.identifier,"profile-id"));
  }
  @Test void checksumPlaceholdersDoNotInventAssertionsAndRealAlgorithmsAreResources() {
    Model graph=ModelFactory.createDefaultModel();Resource distribution=graph.createResource("urn:test:distribution");
    Property link=graph.createProperty("http://spdx.org/rdf/terms#checksum");
    DcatApSerializer.serializeChecksum(new SpdxChecksum(null,"checksumAlgorithm_sha1","","1"),graph,distribution);
    assertFalse(distribution.hasProperty(link));
    SpdxChecksum real=new SpdxChecksum(null,"checksumAlgorithm_sha256","abcd","1");
    // Simulate the transient URI and vocabulary fields after database/JSON reload.
    real.setUri(null);NgsiDcatReader.restoreMetadata(real);
    DcatApSerializer.serializeChecksum(real,graph,distribution);
    Resource value=distribution.getPropertyResourceValue(link);
    assertTrue(value.hasProperty(graph.createProperty("http://spdx.org/rdf/terms#algorithm"),graph.createResource("http://spdx.org/rdf/terms#checksumAlgorithm_sha256")));
    assertEquals("abcd",value.getProperty(graph.createProperty("http://spdx.org/rdf/terms#checksumValue")).getString());
  }
  @Test void licenseNameKeepsFoafPredicateAfterJsonReload() throws Exception {
    DctLicenseDocument license=new DctLicenseDocument();DcatProperty name=new DcatProperty();name.setValue("CC BY");license.setName(name);
    NgsiDcatReader.restoreMetadata(license);
    assertEquals("http://xmlns.com/foaf/0.1/name",license.getName().getProperty().getURI());
  }
  private static final String URI="https://example.org/roundtrip/dataset";
  private static final Property PAGE=ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/page");
  private static final Property ADMS_ID=ResourceFactory.createProperty("http://www.w3.org/ns/adms#identifier");
  private static JSONObject property(Object value) {return new JSONObject().put("type","Property").put("value",value);}
  private static Model output(DcatDataset dataset) {
    DcatApSerializer.nodeResources=new HashMap<>();
    return DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
  }
  private static JSONObject entity() {
    return new JSONObject().put("id","urn:ngsi-ld:Dataset:test").put("type","Dataset")
        .put("resourceUri",property(URI)).put("identifier",property("source-id"))
        .put("title",property(new JSONObject().put("it","Città").put("en","City")))
        .put("description",property(new JSONObject().put("it","Descrizione")))
        .put(PAGE.getURI(),property(new JSONArray().put("https://example.org/doc")))
        .put(ADMS_ID.getURI(),property("https://doi.org/example"));
  }
  @Test void expandedTermsKeepTheirDistinctPredicatesAndDoNotInventPlainTranslations() throws Exception {
    DcatDataset dataset=NgsiDcatReader.dataset(entity(),"999999",id->null);
    assertEquals("source-id",dataset.getIdentifier().getValue());
    assertEquals("https://doi.org/example",dataset.getOtherIdentifier().get(0).getValue());
    Model graph=output(dataset);Resource root=graph.getResource(URI);
    assertTrue(root.hasProperty(PAGE,graph.createResource("https://example.org/doc")));
    assertTrue(root.hasProperty(ADMS_ID));
    assertFalse(root.hasProperty(graph.createProperty(DCTerms.NS+"documentation")));
    assertFalse(root.hasProperty(graph.createProperty(DCTerms.NS+"otherIdentifier")));
    assertEquals(2,root.listProperties(DCTerms.title).toList().size());
    assertTrue(root.listProperties(DCTerms.title).toList().stream().allMatch(s->!s.getLanguage().isEmpty()));
    // A real untagged value must still survive alongside its translations.
    JSONObject withPlain=entity();withPlain.getJSONObject("title").getJSONObject("value").put("@none","東京");
    Resource mixed=output(NgsiDcatReader.dataset(withPlain,"999999",id->null)).getResource(URI);
    assertTrue(mixed.hasProperty(DCTerms.title,"東京"));
    assertTrue(mixed.hasProperty(DCTerms.title,ResourceFactory.createLangLiteral("Città","it")));
    assertEquals(3,mixed.listProperties(DCTerms.title).toList().size());
  }
  @Test void apiVocabularyAndBrokerProjectionReconstructTheSameSourceGraph() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    Resource root=source.createResource(URI,DCAT.Dataset)
        .addProperty(DCTerms.identifier,"source-id")
        .addProperty(DCTerms.title,source.createLiteral("Città","it"))
        .addProperty(DCTerms.title,source.createLiteral("City","en"))
        .addProperty(DCTerms.description,source.createLiteral("Descrizione","it"))
        .addProperty(PAGE,source.createResource("https://example.org/doc"))
        .addProperty(ADMS_ID,source.createResource().addProperty(SKOS.notation,"https://doi.org/example"))
        .addProperty(source.createProperty("https://example.org/sourceNote"),"Evidence survives");
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",root);
    DcatDataset api=GsonUtil.json2Obj(GsonUtil.obj2Json(parsed,GsonUtil.datasetType),GsonUtil.datasetType);
    Model apiGraph=output(api);
    assertEquals(PAGE.getURI(),api.getDocumentation().get(0).getProperty().getURI());
    assertEquals(ADMS_ID.getURI(),api.getOtherIdentifier().get(0).getProperty().getURI());
    JSONObject normalized=new JSONObject(GsonUtil.obj2Json(parsed,GsonUtil.datasetType));
    normalized.remove("documentation");normalized.remove("otherIdentifier");
    JSONObject wire=entity();for(String key:wire.keySet())normalized.put(key,wire.get(key));
    normalized.put("metadataEnvelope",property(parsed.getMetadataEnvelope()));
    Model inverseGraph=output(NgsiDcatReader.dataset(normalized,"999998",id->null));
    if(!apiGraph.isIsomorphicWith(inverseGraph)) {
      try(var out=java.nio.file.Files.newOutputStream(java.nio.file.Path.of("target/roundtrip-test-api.ttl"))) {org.apache.jena.riot.RDFDataMgr.write(out,apiGraph,org.apache.jena.riot.Lang.TURTLE);}
      try(var out=java.nio.file.Files.newOutputStream(java.nio.file.Path.of("target/roundtrip-test-inverse.ttl"))) {org.apache.jena.riot.RDFDataMgr.write(out,inverseGraph,org.apache.jena.riot.Lang.TURTLE);}
    }
    assertTrue(apiGraph.isIsomorphicWith(inverseGraph),"API and normalized broker projection must agree, including envelope branches");
    assertTrue(inverseGraph.getResource(URI).hasProperty(source.createProperty("https://example.org/sourceNote"),"Evidence survives"));
  }
  @Test void extendedLanguageTagsSurviveXmlParsingAndNormalizedJson() throws Exception {
    String tag="cs-t-en-t0-mtec";
    String xml="<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#' xmlns:dcat='http://www.w3.org/ns/dcat#' xmlns:dct='http://purl.org/dc/terms/'><dcat:Dataset rdf:about='"+URI+"'><dct:title xml:lang='"+tag+"'>Město</dct:title></dcat:Dataset></rdf:RDF>";
    it.eng.idra.beans.odms.OdmsCatalogue node=new it.eng.idra.beans.odms.OdmsCatalogue();node.setHost("https://example.org");
    DcatApDeserializer parser=new DcatApDeserializer();Model source=parser.dumpToModel(xml,node);
    assertTrue(source.getResource(URI).hasProperty(DCTerms.title,source.createLiteral("Město",tag)));
    DcatDataset parsed=parser.resourceToDataset("999999",source.getResource(URI));
    assertTrue(output(parsed).getResource(URI).hasProperty(DCTerms.title,source.createLiteral("Město",tag)));
    JSONObject input=entity();input.put("title",property(new JSONObject().put(tag,"Město")));
    Resource inverse=output(NgsiDcatReader.dataset(input,"999998",id->null)).getResource(URI);
    assertTrue(inverse.hasProperty(DCTerms.title,source.createLiteral("Město",tag)));
    assertEquals(1,inverse.listProperties(DCTerms.title).toList().size());
  }
  @Test void uneditedSourceDatesKeepTheirPrecisionWhileTypedEditsStillTakeEffect() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    Resource root=source.createResource(URI,DCAT.Dataset).addProperty(DCTerms.title,"Calendar date")
        .addProperty(DCTerms.issued,source.createTypedLiteral("2026-10-20",org.apache.jena.datatypes.xsd.XSDDatatype.XSDdate));
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",root);
    Literal before=output(parsed).getResource(URI).getProperty(DCTerms.issued).getLiteral();
    assertEquals("2026-10-20",before.getLexicalForm());assertEquals(XSD.date.getURI(),before.getDatatypeURI());
    parsed.getReleaseDate().setValue("2026-10-21T10:00:00+02:00");
    Literal edited=output(parsed).getResource(URI).getProperty(DCTerms.issued).getLiteral();
    assertEquals("2026-10-21T10:00:00+02:00",edited.getLexicalForm());assertEquals(XSD.dateTime.getURI(),edited.getDatatypeURI());
  }
  @Test void typedDatasetPublisherDoesNotRequireAnEnclosingCatalogue() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    Resource publisher=source.createResource("https://example.org/publisher")
        .addProperty(ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/name"),"City publisher");
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",source.createResource(URI,DCAT.Dataset)
        .addProperty(DCTerms.title,"City data").addProperty(DCTerms.publisher,publisher));
    parsed.setMetadataEnvelope(null);
    Model graph=output(parsed);
    assertTrue(graph.getResource(URI).hasProperty(DCTerms.publisher,graph.getResource(publisher.getURI())));
    assertTrue(graph.getResource(publisher.getURI()).hasProperty(ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/name"),"City publisher"));
  }
}
