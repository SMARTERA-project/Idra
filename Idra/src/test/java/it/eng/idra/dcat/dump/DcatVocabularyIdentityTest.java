package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatVocabularyIdentityTest {
  @Test void completeExportsEmitExactlyOneMediaTypeForLegacyCodesInBothProfiles() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    RDFParser.fromString("@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. <urn:test:dataset> a dcat:Dataset; dct:title \"Title\"; dct:description \"Description\"; dcat:distribution <urn:test:distribution>. <urn:test:distribution> a dcat:Distribution; dcat:accessURL <https://example.org/data>; dcat:downloadURL <https://example.org/data.csv>.").lang(Lang.TURTLE).parse(source);
    for(DcatApDeserializer parser:Arrays.asList(new DcatApDeserializer(),new DcatApItDeserializer())) {
      DcatDataset dataset=parser.resourceToDataset("999999",source.getResource("urn:test:dataset"));
      DcatDistribution distribution=dataset.getDistributions().get(0);distribution.setMediaType("text/csv");distribution.setFormat("CSV");
      DcatApSerializer.nodeResources=new HashMap<>();Model output=ModelFactory.createDefaultModel();
      if(parser instanceof DcatApItDeserializer)DcatApItSerializer.addDatasetToModel(dataset,output);else DcatApSerializer.addDatasetToModel(dataset,output);
      assertEquals(1,output.listObjectsOfProperty(output.getResource("urn:test:distribution"),DCAT.mediaType).toList().size());
      assertTrue(output.getResource("urn:test:distribution").hasProperty(DCAT.mediaType,output.createResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
      assertEquals("text/csv",distribution.getMediaType().getValue());assertEquals("CSV",distribution.getFormat().getValue());
    }
  }
  @Test void unrecognizedSourceThemeLabelsArePreservedWithoutInventingAnAuthority() {
    List<SkosConceptTheme> concepts=it.eng.idra.utils.SkosConceptFactory.build(DCAT.theme.getURI(),Arrays.asList("Citoyenneté"),SkosConceptTheme.class,"999999");
    assertEquals(1,concepts.size());assertTrue(concepts.get(0).getResourceUri().startsWith("urn:idra:concept:999999:"));
    assertEquals("Citoyenneté",concepts.get(0).getPrefLabel().get(0).getValue());
  }
  @Test void ngsiEntityTypeIsNotAResourceClassification() {
    org.json.JSONObject entity=new org.json.JSONObject().put("id","urn:test:dataset").put("type","Dataset");
    DcatDataset dataset=it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null);
    assertEquals("",dataset.getType().getValue());
    entity.put("resourceType",new org.json.JSONObject().put("type","Property").put("value","http://publications.europa.eu/resource/authority/dataset-type/STATISTICAL"));
    assertEquals("http://publications.europa.eu/resource/authority/dataset-type/STATISTICAL",it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null).getType().getValue());
  }
  @Test void catalogueUsesItsConfiguredPublisherSeparatelyFromTheDataset() throws Exception {
    it.eng.idra.beans.odms.OdmsCatalogue node=new it.eng.idra.beans.odms.OdmsCatalogue();
    node.setId(999999);node.setHost("https://example.org/catalogue");node.setHomepage("https://example.org");node.setName("Catalogue");node.setDescription("Description");node.setPublisherName("Catalogue publisher");node.setPublisherUrl("https://example.org/publisher");
    DcatApSerializer.nodeResources=new HashMap<>();DcatApSerializer.nodeResources.put(node.getId(),node);
    Model source=ModelFactory.createDefaultModel();RDFParser.fromString("@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. <urn:test:dataset> a dcat:Dataset; dct:title \"Title\"; dct:description \"Description\".").lang(Lang.TURTLE).parse(source);
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",source.getResource("urn:test:dataset"));
    Model output=ModelFactory.createDefaultModel();DcatApSerializer.addDatasetToModel(dataset,output);
    Resource catalogue=output.getResource(node.getHost());
    assertTrue(catalogue.hasProperty(DCTerms.publisher,output.createResource("https://example.org/publisher")));
    assertTrue(output.getResource("https://example.org/publisher").hasProperty(org.apache.jena.sparql.vocabulary.FOAF.name,"Catalogue publisher"));
    assertFalse(output.getResource("urn:test:dataset").hasProperty(DCTerms.publisher,output.createResource("https://example.org/publisher")));
  }
  @Test void serializationPreservesAbsoluteUrisAndDoesNotMutateValues() {
    for(String value:Arrays.asList("http://publications.europa.eu/resource/authority/file-type/CSV","https://example.org/custom-format","urn:example:format:csv","OGC:WMS")) {
      DcatProperty format=new DcatProperty(DCTerms.format,DCTerms.MediaTypeOrExtent,value);
      Model model=ModelFactory.createDefaultModel();Resource distribution=model.createResource("urn:test:distribution");
      DcatApSerializer.serializeFormat(format,model,distribution);
      DcatApSerializer.serializeFormat(format,model,distribution);
      assertEquals(value,format.getValue());
      assertEquals(Collections.singleton(model.createResource(value)),model.listObjectsOfProperty(distribution,DCTerms.format).toSet());
    }
    String value="https://www.iana.org/assignments/media-types/text/csv";
    DcatProperty media=new DcatProperty(DCAT.mediaType,DCTerms.MediaType,value);
    Model model=ModelFactory.createDefaultModel();Resource distribution=model.createResource("urn:test:distribution");
    DcatApSerializer.serializeMediaType(media,model,distribution);DcatApSerializer.serializeMediaType(media,model,distribution);
    assertEquals(value,media.getValue());
    assertEquals(Collections.singleton(model.createResource(value)),model.listObjectsOfProperty(distribution,DCAT.mediaType).toSet());
  }
  @Test void preservesVocabularyIdentityThroughBothRdfProfiles() throws Exception {
    String text="@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. <urn:test:dataset> a dcat:Dataset; dct:title \"Titolo\"@it; dct:description \"Dati\"@it; dcat:distribution <urn:test:distribution>. <urn:test:distribution> a dcat:Distribution; dcat:accessURL <https://example.org/data>; dct:format <https://example.org/custom-format>; dcat:mediaType <https://www.iana.org/assignments/media-types/text/csv>.";
    Model source=ModelFactory.createDefaultModel();RDFParser.fromString(text).lang(Lang.TURTLE).parse(source);
    for(DcatApDeserializer parser:Arrays.asList(new DcatApDeserializer(),new DcatApItDeserializer())) {
      DcatDataset dataset=parser.resourceToDataset("999999",source.getResource("urn:test:dataset"));
      assertEquals("https://example.org/custom-format",dataset.getDistributions().get(0).getFormat().getValue());
      assertEquals("https://www.iana.org/assignments/media-types/text/csv",dataset.getDistributions().get(0).getMediaType().getValue());
      Model output=ModelFactory.createDefaultModel();DcatApSerializer.nodeResources=new HashMap<>();
      if(parser instanceof DcatApItDeserializer)DcatApItSerializer.addDatasetToModel(dataset,output);else DcatApSerializer.addDatasetToModel(dataset,output);
      Resource distribution=output.getResource("urn:test:distribution");
      assertTrue(distribution.hasProperty(DCTerms.format,output.createResource("https://example.org/custom-format")));
      assertTrue(distribution.hasProperty(DCAT.mediaType,output.createResource("https://www.iana.org/assignments/media-types/text/csv")));
    }
  }
  @Test void legacyCodesRemainCompatibleWithoutMutation() {
    Model model=ModelFactory.createDefaultModel();Resource distribution=model.createResource("urn:test:distribution");
    DcatProperty format=new DcatProperty(DCTerms.format,DCTerms.MediaTypeOrExtent,"CSV");
    DcatProperty media=new DcatProperty(DCAT.mediaType,DCTerms.MediaType,"text/csv");
    DcatApSerializer.serializeFormat(format,model,distribution);DcatApSerializer.serializeMediaType(media,model,distribution);
    assertEquals("CSV",format.getValue());assertEquals("text/csv",media.getValue());
    assertTrue(distribution.hasProperty(DCTerms.format,model.createResource(DcatApSerializer.FORMAT_BASE_URI+"CSV")));
    assertTrue(distribution.hasProperty(DCAT.mediaType,model.createResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
  }
  @Test void normalizedNgsiListsCanBeSerializedWithoutMissingPredicates() {
    org.json.JSONObject entity=new org.json.JSONObject("{\"id\":\"urn:test:dataset\",\"type\":\"Dataset\",\"title\":{\"type\":\"Property\",\"value\":\"Title\"},\"description\":{\"type\":\"Property\",\"value\":\"Description\"},\"language\":{\"type\":\"Property\",\"value\":[\"http://publications.europa.eu/resource/authority/language/ENG\"]},\"applicableLegislation\":{\"type\":\"Property\",\"value\":[\"http://data.europa.eu/eli/reg_impl/2023/138/oj\"]}}");
    DcatDataset dataset=it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null);
    assertEquals(DCTerms.language,dataset.getLanguage().get(0).getProperty());
    assertEquals(DCATAP.applicableLegislation,dataset.getApplicableLegislation().get(0).getProperty());
    Model output=ModelFactory.createDefaultModel();DcatApSerializer.nodeResources=new HashMap<>();
    assertDoesNotThrow(()->DcatApSerializer.addDatasetToModel(dataset,output));
    assertTrue(output.getResource("urn:test:dataset").hasProperty(DCTerms.language,output.createResource("http://publications.europa.eu/resource/authority/language/ENG")));
  }
  @Test void normalizedNgsiDistributionKeepsAccessAndDownloadUrls() {
    org.json.JSONObject entity=new org.json.JSONObject("{\"id\":\"urn:test:distribution\",\"type\":\"DistributionDCAT-AP\",\"resourceUri\":{\"type\":\"Property\",\"value\":\"https://example.org/distribution\"},\"accessURL\":{\"type\":\"Property\",\"value\":\"https://example.org/access\"},\"downloadURL\":{\"type\":\"Property\",\"value\":\"https://example.org/download\"}}");
    entity.put("format",new org.json.JSONObject().put("type","Property").put("value","CSV"));
    entity.put("mediaType",new org.json.JSONObject().put("type","Property").put("value","text/csv"));
    DcatDistribution distribution=it.eng.idra.utils.NgsiDcatReader.distribution(entity,"999999",id->null);
    assertNotNull(distribution.getAccessUrl());assertNotNull(distribution.getDownloadUrl());
    assertEquals("https://example.org/access",distribution.getAccessUrl().getValue());
    assertEquals("https://example.org/download",distribution.getDownloadUrl().getValue());
    assertEquals(DCTerms.MediaTypeOrExtent,distribution.getFormat().getRange());
    assertEquals(DCTerms.MediaType,distribution.getMediaType().getRange());
  }
}
