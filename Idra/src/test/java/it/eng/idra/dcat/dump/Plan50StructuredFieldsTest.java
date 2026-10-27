package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.utils.GsonUtil;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Plan50 step 3: DCAT-AP 3 DataService fields survive the structured projection (Solr JSON -> API) without the envelope.
class Plan50StructuredFieldsTest {
  private static final String NS="https://example.org/";

  private static Resource service(Model m) {
    Resource agent=m.createResource(NS+"agent",FOAF.Agent).addProperty(FOAF.name,"Service publisher");
    return m.createResource(NS+"service",DCAT.DataService)
        .addProperty(DCTerms.title,m.createLiteral("Servizio","it")).addProperty(DCTerms.title,m.createLiteral("Service","en"))
        .addProperty(DCTerms.description,m.createLiteral("Description","en"))
        .addProperty(DCAT.keyword,m.createLiteral("api","en")).addProperty(DCAT.theme,m.createResource(NS+"theme"))
        .addProperty(DCTerms.conformsTo,m.createResource(NS+"standard")).addProperty(DCAT.landingPage,m.createResource(NS+"landing"))
        .addProperty(DCTerms.format,m.createResource(NS+"format")).addProperty(FOAF.page,m.createResource(NS+"doc"))
        .addProperty(DCTerms.publisher,agent).addProperty(DCAT.endpointURL,m.createResource(NS+"api"));
  }

  private static void assertService(Model out) {
    Resource s=out.getResource(NS+"service");
    assertEquals(Set.of(out.createLiteral("Servizio","it"),out.createLiteral("Service","en")),new HashSet<>(out.listObjectsOfProperty(s,DCTerms.title).toList()));
    assertTrue(out.contains(s,DCTerms.description,out.createLiteral("Description","en")));
    assertTrue(out.contains(s,DCAT.keyword,out.createLiteral("api","en")));
    for(Property p:List.of(DCAT.theme,DCTerms.conformsTo,DCAT.landingPage,DCTerms.format,FOAF.page))
      assertEquals(1,out.listObjectsOfProperty(s,p).toList().size(),p.toString());
    assertTrue(out.contains(s,DCTerms.publisher,out.getResource(NS+"agent")));
    assertTrue(out.contains(out.getResource(NS+"agent"),FOAF.name,(RDFNode)null));
  }

  @Test void catalogueServiceKeepsDcatAp3Fields() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    DcatDataService parsed=new DcatApDeserializer().resourceToDataService(service(m),"999999");
    Model out=ModelFactory.createDefaultModel();Resource r=out.createResource(NS+"service",DCAT.DataService);
    DcatRdfMetadata.writeService(parsed,r);
    assertService(out);
  }

  @Test void accessServiceSurvivesTheSolrProjection() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    Resource distribution=m.createResource(NS+"distribution",DCAT.Distribution).addProperty(DCAT.accessURL,m.createResource(NS+"api"))
        .addProperty(DCAT.accessService,service(m));
    Resource dataset=m.createResource(NS+"dataset",DCAT.Dataset).addProperty(DCTerms.title,"Dataset").addProperty(DCTerms.description,"D")
        .addProperty(DCAT.distribution,distribution);
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",dataset);
    List<DcatDataService> services=parsed.getDistributions().get(0).getAccessService();
    // As stored in Solr and read back for the API.
    String stored=GsonUtil.obj2Json(services,GsonUtil.dataServiceListType);
    List<DcatDataService> reloaded=DcatDataService.jsonArrayToDcatDataService(new JSONArray(stored),"999999");
    assertEquals(NS+"service",reloaded.get(0).getResourceUri(),"identity is not replaced by urn:idra:service");
    parsed.getDistributions().get(0).setAccessService(reloaded);
    parsed.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(parsed,ModelFactory.createDefaultModel());
    assertService(out);
    assertEquals(2,out.listObjectsOfProperty(out.getResource(NS+"service"),DCTerms.title).toList().size(),"no extra legacy title beside the language variants");
  }

  @Test void locationIriAndDistributionIdentityStayInTheProjection() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    Resource location=m.createResource(NS+"location",DCTerms.Location).addProperty(m.createProperty("http://www.w3.org/ns/locn#geometry"),"POINT(1 2)");
    Resource distribution=m.createResource(NS+"distribution",DCAT.Distribution).addProperty(DCAT.accessURL,m.createResource(NS+"file"));
    Resource dataset=m.createResource(NS+"dataset",DCAT.Dataset).addProperty(DCTerms.title,"Dataset").addProperty(DCTerms.description,"D")
        .addProperty(DCTerms.spatial,location).addProperty(DCAT.distribution,distribution);
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",dataset);
    parsed.setMetadataEnvelope(null);
    // Solr round trip of the distribution (the API reads datasets back from the index).
    DcatDistribution stored=parsed.getDistributions().get(0);stored.setId("d1");
    org.apache.solr.common.SolrDocument doc=new org.apache.solr.common.SolrDocument();
    stored.toDoc().forEach(field -> doc.setField(field.getName(),field.getValue()));
    DcatDistribution reloaded=DcatDistribution.docToDcatDistribution(doc);
    assertEquals(NS+"distribution",reloaded.getResourceUri());
    Model out=DcatApSerializer.addDatasetToModel(parsed,ModelFactory.createDefaultModel());
    Resource exported=out.getResource(NS+"dataset");
    assertEquals(List.of(out.getResource(NS+"location")),out.listObjectsOfProperty(exported,DCTerms.spatial).toList(),"dct:spatial keeps the location IRI");
    assertFalse(out.contains(out.getResource(NS+"location"),RDFS.seeAlso,(RDFNode)null),"the IRI is not repeated as a literal");
    RDFNode geometry=out.getResource(NS+"location").getProperty(out.createProperty("http://www.w3.org/ns/locn#geometry")).getObject();
    assertEquals("http://www.opengis.net/ont/geosparql#wktLiteral",geometry.asLiteral().getDatatypeURI(),"WKT keeps its datatype");
  }

  @Test void brokerDistributionKeepsEveryLanguage() throws Exception {
    org.json.JSONObject distribution=new org.json.JSONObject().put("id","urn:ngsi-ld:DistributionDCAT-AP:id:d1").put("type","DistributionDCAT-AP")
        .put("title",new org.json.JSONObject().put("type","Property").put("value",new org.json.JSONObject().put("it","Titolo").put("en","Title")))
        .put("description",new org.json.JSONObject().put("type","Property").put("value",new org.json.JSONObject().put("it","Descrizione").put("en","Description")))
        .put("accessURL",new org.json.JSONObject().put("type","Property").put("value",NS+"file"))
        .put("resourceUri",new org.json.JSONObject().put("type","Property").put("value",NS+"distribution"));
    org.json.JSONObject dataset=new org.json.JSONObject().put("id","urn:ngsi-ld:Dataset:id:x").put("type","Dataset")
        .put("title",new org.json.JSONObject().put("type","Property").put("value","Dataset"))
        .put("resourceUri",new org.json.JSONObject().put("type","Property").put("value",NS+"dataset"))
        .put("datasetDistribution",new org.json.JSONObject().put("type","Relationship").put("object",new JSONArray().put(distribution.getString("id"))));
    DcatDataset read=it.eng.idra.utils.NgsiDcatReader.dataset(dataset,"999999",id -> id.equals(distribution.getString("id"))?distribution:null);
    Model out=DcatApSerializer.addDatasetToModel(read,ModelFactory.createDefaultModel());
    Resource d=out.getResource(NS+"distribution");
    assertEquals(Set.of(out.createLiteral("Titolo","it"),out.createLiteral("Title","en")),new HashSet<>(out.listObjectsOfProperty(d,DCTerms.title).toList()));
    assertEquals(2,out.listObjectsOfProperty(d,DCTerms.description).toList().size());
  }
}
