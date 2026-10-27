package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.*;
import java.util.*;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Plan50 step 2: the source dcat:Catalog description is kept beside Idra's catalogue configuration and exported.
class Plan50CatalogueSourceTest {
  private static final String NS="https://example.org/";
  private static final Property RIGHTS=DCTerms.rights;

  @AfterEach void reset() { DcatApSerializer.nodeResources=null; }

  private static Model source() {
    Model m=ModelFactory.createDefaultModel();
    Resource scheme=m.createResource(NS+"themes",SKOS.ConceptScheme).addProperty(DCTerms.title,m.createLiteral("Themes","en"));
    Resource publisher=m.createResource(NS+"agent/1",FOAF.Agent).addProperty(FOAF.name,"Source publisher");
    Resource rights=m.createResource().addProperty(RDF.type,DCTerms.RightsStatement).addProperty(RDFS.label,"Open");
    Resource c=m.createResource(NS+"catalog",DCAT.Catalog)
        .addProperty(DCTerms.title,m.createLiteral("Titolo","it")).addProperty(DCTerms.title,m.createLiteral("Title","en"))
        .addProperty(DCTerms.description,m.createLiteral("Description","en"))
        .addProperty(DCTerms.publisher,publisher)
        .addProperty(DCTerms.issued,"2024-01-15",XSDDatatype.XSDdate)
        .addProperty(DCTerms.modified,"2024-06-30T10:20:30+00:00",XSDDatatype.XSDdateTime)
        .addProperty(DCTerms.language,m.createResource("http://publications.europa.eu/resource/authority/language/ITA"))
        .addProperty(DCTerms.license,m.createResource(NS+"license"))
        .addProperty(RIGHTS,rights)
        .addProperty(FOAF.homepage,m.createResource(NS+"home"))
        .addProperty(DCAT.themeTaxonomy,scheme)
        .addProperty(DCTerms.spatial,m.createResource(NS+"location/1"))
        .addProperty(DCTerms.hasPart,m.createResource(NS+"part"))
        .addProperty(DCTerms.isPartOf,m.createResource(NS+"parent"))
        .addProperty(DCAT.catalog,m.createResource(NS+"child",DCAT.Catalog).addProperty(DCTerms.title,"Child catalogue"));
    for(int i=1;i<=2;i++) {
      Resource d=m.createResource(NS+"dataset/"+i,DCAT.Dataset).addProperty(DCTerms.title,"Dataset "+i).addProperty(DCTerms.description,"D");
      c.addProperty(DCAT.dataset,d);
    }
    return m;
  }

  private static OdmsCatalogue node() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("");node.setHomepage("https://idra.example/home");
    node.setName("Idra configured name");node.setDescription("Idra configured description");
    node.setPublisherName("Idra publisher");node.setPublisherUrl("https://idra.example/publisher");
    return node;
  }

  private static Model export(OdmsCatalogue node,Model source) throws Exception {
    DcatApSerializer.nodeResources=Map.of(node.getId(),node);
    Model out=ModelFactory.createDefaultModel();
    for(int i=1;i<=2;i++)DcatApSerializer.addDatasetToModel(new DcatApDeserializer().resourceToDataset(String.valueOf(node.getId()),
        source.getResource(NS+"dataset/"+i)),out);
    return out;
  }

  @Test void sourceCatalogueMetadataIsCapturedWithoutMembers() throws Exception {
    Model m=source();OdmsCatalogue node=node();
    new DcatApDeserializer().readCatalogue(m,node);
    assertNotNull(node.getSourceMetadata());
    String rdf=new org.json.JSONObject(node.getSourceMetadata()).getString("sourceRdf");
    assertTrue(rdf.contains("Titolo") && rdf.contains("themes") && rdf.contains(NS+"child"));
    assertFalse(rdf.contains("Dataset 1"),"datasets are persisted separately, not inside the catalogue evidence");
    // DCAT-AP range of dcat:catalog: the linked sub-catalogue keeps its class and description, not its members.
    assertTrue(rdf.contains("Child catalogue"),"the sub-catalogue is described as a dcat:Catalog");
    assertEquals("Idra configured name",node.getName(),"Idra's configuration is not overwritten");
  }

  @Test void exportUsesTheSourceCatalogueAndDescribesItOnce() throws Exception {
    Model m=source();OdmsCatalogue node=node();
    new DcatApDeserializer().readCatalogue(m,node);
    Model out=export(node,m);
    Resource c=out.getResource("https://idra.example/home");
    assertEquals(Set.of(out.createLiteral("Titolo","it"),out.createLiteral("Title","en")),new HashSet<>(out.listObjectsOfProperty(c,DCTerms.title).toList()));
    assertEquals(List.of(out.createLiteral("Description","en")),out.listObjectsOfProperty(c,DCTerms.description).toList());
    assertEquals(List.of(out.getResource(NS+"agent/1")),out.listObjectsOfProperty(c,DCTerms.publisher).toList());
    assertTrue(out.contains(out.getResource(NS+"agent/1"),FOAF.name,"Source publisher"));
    assertFalse(out.contains(null,FOAF.name,"Idra publisher"),"the replaced configured publisher is not left dangling");
    assertEquals(List.of(out.createTypedLiteral("2024-01-15",XSDDatatype.XSDdate)),out.listObjectsOfProperty(c,DCTerms.issued).toList());
    assertEquals(1,out.listObjectsOfProperty(c,DCTerms.modified).toList().size());
    for(Property p:List.of(DCTerms.language,DCTerms.license,FOAF.homepage,DCAT.themeTaxonomy,DCTerms.hasPart,DCTerms.isPartOf,DCAT.catalog))
      assertEquals(1,out.listObjectsOfProperty(c,p).toList().size(),p.toString());
    assertEquals(List.of(out.getResource(NS+"location/1")),out.listObjectsOfProperty(c,DCTerms.spatial).toList());
    assertTrue(out.contains(out.getResource(NS+"themes"),DCTerms.title,out.createLiteral("Themes","en")));
    Resource rights=c.getPropertyResourceValue(RIGHTS);
    assertTrue(rights.isAnon() && out.contains(rights,RDFS.label,"Open"));
    assertEquals(1,out.listResourcesWithProperty(RDFS.label,"Open").toList().size(),"blank nodes are not duplicated per dataset");
    assertEquals(2,out.listObjectsOfProperty(c,DCAT.dataset).toList().size());
  }

  @Test void configurationStillDescribesACatalogueWithoutSourceMetadata() throws Exception {
    // Non-DCAT connectors (and catalogues not yet re-synchronised) keep the previous behaviour.
    Model m=source();OdmsCatalogue node=node();
    Model out=export(node,m);
    Resource c=out.getResource("https://idra.example/home");
    assertEquals(List.of(out.createLiteral("Idra configured name")),out.listObjectsOfProperty(c,DCTerms.title).toList());
    assertTrue(out.contains(null,FOAF.name,"Idra publisher"));
  }

  @Test void configurationFillsPropertiesTheSourceDoesNotHave() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    Resource c=m.createResource(NS+"catalog",DCAT.Catalog).addProperty(FOAF.homepage,m.createResource(NS+"home"));
    for(int i=1;i<=2;i++)c.addProperty(DCAT.dataset,m.createResource(NS+"dataset/"+i,DCAT.Dataset).addProperty(DCTerms.title,"Dataset "+i));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(m,node);
    Model out=export(node,m);Resource exported=out.getResource("https://idra.example/home");
    assertEquals(List.of(out.createLiteral("Idra configured name")),out.listObjectsOfProperty(exported,DCTerms.title).toList());
    assertEquals(List.of(out.getResource(NS+"home")),out.listObjectsOfProperty(exported,FOAF.homepage).toList());
  }

  @Test void catalogueWithoutOwnDescriptionStoresNothing() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    Resource c=m.createResource(NS+"catalog",DCAT.Catalog);
    c.addProperty(DCAT.dataset,m.createResource(NS+"dataset/1",DCAT.Dataset).addProperty(DCTerms.title,"Dataset"));
    OdmsCatalogue node=node();node.setSourceMetadata("stale");
    new DcatApDeserializer().readCatalogue(m,node);
    assertNull(node.getSourceMetadata(),"a re-sync without catalogue metadata clears the previous evidence");
  }

  @Test void catalogueRecordSourceIsReadAndExported() throws Exception {
    // Plan50 step 3: dct:source of a dcat:CatalogRecord (the original record) was dropped.
    Model m=ModelFactory.createDefaultModel();
    Resource c=m.createResource(NS+"catalog",DCAT.Catalog);
    Resource d=m.createResource(NS+"dataset/1",DCAT.Dataset).addProperty(DCTerms.title,"Dataset");c.addProperty(DCAT.dataset,d);
    c.addProperty(DCAT.record,m.createResource(NS+"record/1",DCAT.CatalogRecord).addProperty(FOAF.primaryTopic,d)
        .addProperty(DCTerms.source,m.createResource("https://origin.example/record/7")));
    c.addProperty(DCAT.record,m.createResource(NS+"record/2",DCAT.CatalogRecord)
        .addProperty(DCTerms.source,m.createResource().addProperty(RDF.type,DCAT.CatalogRecord)));
    OdmsCatalogue node=node();new DcatApDeserializer().readCatalogue(m,node);
    Map<String,DcatCatalogueRecord> records=new HashMap<>();for(DcatCatalogueRecord r:node.getRecord())records.put(r.getResourceUri(),r);
    assertEquals("https://origin.example/record/7",records.get(NS+"record/1").getSource().getValue());
    assertNull(records.get(NS+"record/2").getSource(),"a blank original record has no identity to keep");
    // As reloaded by JPA: only the value survives.
    DcatProperty reloaded=new DcatProperty();reloaded.setValue("https://origin.example/record/7");records.get(NS+"record/1").setSource(reloaded);
    Model out=ModelFactory.createDefaultModel();Resource exported=out.createResource("https://idra.example/home",DCAT.Catalog);
    DcatRdfMetadata.writeCatalogue(node,exported);
    assertTrue(out.contains(out.getResource(NS+"record/1"),DCTerms.source,out.getResource("https://origin.example/record/7")));
    assertFalse(out.contains(out.getResource(NS+"record/2"),DCTerms.source,(RDFNode)null));
  }
}
