package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.*;
import it.eng.idra.cache.CachePersistenceManager;
import it.eng.idra.management.PersistenceManager;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Regressions for the five blockers found by the plan50 DCAT-AP 3.0.1 coverage matrix (B1-B5).
class Plan50BlockersTest {
  private static final String TYPE="http://publications.europa.eu/resource/authority/publisher-type/NationalAuthority";

  @Test void agentTypeAndMailboxIrisDoNotDropTheDataset() throws Exception {
    // B1: dct:type of a foaf:Agent is a concept IRI in DCAT-AP; reading it as a literal skipped the whole dataset.
    Model source=ModelFactory.createDefaultModel();
    Resource agent=source.createResource("https://example.org/agent",FOAF.Agent).addProperty(FOAF.name,"Agency")
        .addProperty(DCTerms.type,source.createResource(TYPE)).addProperty(FOAF.mbox,source.createResource("mailto:info@example.org"));
    Resource root=source.createResource("https://example.org/dataset",DCAT.Dataset).addProperty(DCTerms.title,"Dataset")
        .addProperty(DCTerms.publisher,agent).addProperty(DCTerms.creator,"Plain-text creator");
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",root);
    assertEquals(TYPE,dataset.getPublisher().getType().getValue());
    assertEquals("mailto:info@example.org",dataset.getPublisher().getMbox().getValue());
    Model out=ModelFactory.createDefaultModel();DcatApSerializer.addDatasetToModel(dataset,out);
    assertTrue(out.contains(out.getResource("https://example.org/agent"),DCTerms.type,out.getResource(TYPE)),"agent type stays an IRI");
    assertTrue(out.contains(out.getResource("https://example.org/agent"),FOAF.mbox,out.getResource("mailto:info@example.org")));
  }

  @Test void nestedCataloguesStillReadTheDescribedCatalogue() {
    // B2: a dump with sub-catalogues (dcat:catalog / dct:hasPart) lost records and services silently.
    Model m=ModelFactory.createDefaultModel();
    Resource root=m.createResource("https://example.org/catalog",DCAT.Catalog).addProperty(DCTerms.title,"Root");
    Resource child=m.createResource("https://example.org/catalog/child",DCAT.Catalog).addProperty(DCTerms.title,"Child");
    Resource parent=m.createResource("https://example.org/catalog/parent",DCAT.Catalog).addProperty(DCTerms.title,"Parent");
    root.addProperty(DCAT.catalog,child).addProperty(DCTerms.isPartOf,parent);
    m.createResource("https://example.org/dataset",DCAT.Dataset);root.addProperty(DCAT.dataset,m.getResource("https://example.org/dataset"));
    Resource record=m.createResource("https://example.org/record",DCAT.CatalogRecord).addProperty(FOAF.primaryTopic,m.getResource("https://example.org/dataset"));
    root.addProperty(DCAT.record,record);
    Resource service=m.createResource("https://example.org/service",DCAT.DataService).addProperty(DCTerms.title,"Service")
        .addProperty(DCAT.endpointURL,m.createResource("https://example.org/api"));
    root.addProperty(DCAT.service,service);
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("technical-host");node.setHomepage("https://example.org/home");
    new DcatApDeserializer().readCatalogue(m,node);
    assertEquals(1,node.getRecord().size());
    assertEquals("https://example.org/record",node.getRecord().get(0).getResourceUri());
    assertEquals(1,node.getService().size());
    // Two unrelated roots without listings stay ambiguous rather than picking one at random.
    Model two=ModelFactory.createDefaultModel();
    List<Resource> cats=List.of(two.createResource("https://a.example/c",DCAT.Catalog),two.createResource("https://b.example/c",DCAT.Catalog));
    assertNull(DcatApDeserializer.rootCatalogue(two,cats));
  }

  @Test void reloadedCatalogueCreatorKeepsTheCatalogueExport() {
    // B6: a creator read back from SQL has no (transient) propertyUri; the null predicate aborted the catalogue node in the export.
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);
    // As reloaded by JPA: values present, transient predicates (on the agent and on each DcatProperty) null.
    FoafAgent creator=new FoafAgent();creator.setResourceUri("https://example.org/creator");
    DcatProperty name=new DcatProperty();name.setValue("Creator name");creator.setName(new ArrayList<>(List.of(name)));
    DcatProperty type=new DcatProperty();type.setValue("http://publications.europa.eu/resource/authority/publisher-type/NationalAuthority");creator.setType(type);
    node.setCreator(creator);
    DcatCatalogueRecord record=new DcatCatalogueRecord();record.setResourceUri("https://example.org/record");node.setRecord(new ArrayList<>(List.of(record)));
    Model m=ModelFactory.createDefaultModel();Resource catalogue=m.createResource("https://example.org/catalog",DCAT.Catalog);
    DcatRdfMetadata.writeCatalogue(node,catalogue);
    assertTrue(m.contains(catalogue,DCTerms.creator,m.getResource("https://example.org/creator")));
    assertTrue(m.contains(m.getResource("https://example.org/creator"),FOAF.name,"Creator name"));
    assertTrue(m.contains(m.getResource("https://example.org/creator"),DCTerms.type,m.getResource("http://publications.europa.eu/resource/authority/publisher-type/NationalAuthority")));
    assertTrue(m.contains(catalogue,DCAT.record,m.getResource("https://example.org/record")),"catalogue content after the creator is still written");
  }
  @Test void catalogueCreatorSurvivesResyncAndRemoval() throws Exception {
    try(SessionFactory factory=h2("plan50Creator")) {
      EntityManager em=factory.createEntityManager();
      try {
        OdmsCatalogue node=catalogue(em);
        PersistenceManager manager=new PersistenceManager();Field field=PersistenceManager.class.getDeclaredField("em");field.setAccessible(true);
        EntityManager original=(EntityManager)field.get(manager);
        try {
          field.set(manager,em);
          // B3: every sync re-reads the creator as a new bean; the second update used to fail with a detached FoafAgent.
          for(int sync=0;sync<3;sync++) {
            node.setCreator(new FoafAgent(DCTerms.creator.getURI(),"https://example.org/creator",List.of("Creator"),"","","","",String.valueOf(node.getId())));
            manager.jpaUpdateOdmsCatalogue(node);em.clear();
          }
          // The same detached creator (identity already assigned) reused by a state update: the QA failure.
          manager.jpaUpdateOdmsCatalogue(node);em.clear();
          assertEquals("https://example.org/creator",manager.jpaGetOdmsCatalogue(node.getId(),true).getCreator().getResourceUri());
          assertEquals(1,count(em,"SELECT COUNT(*) FROM dcat_agent"),"no duplicated creator rows");
        } finally {field.set(manager,original);if(original!=null)original.close();}
        // B4: removing the catalogue data deleted the creator agent while odms.creator_id still referenced it.
        CachePersistenceManager cache=cache(em);
        cache.jpaDeleteDatasetsByOdmsNode(node.getId());
        assertEquals(0,count(em,"SELECT COUNT(*) FROM dcat_agent"));
        assertEquals(0,count(em,"SELECT COUNT(*) FROM odms WHERE creator_id IS NOT NULL"));
      } finally {em.close();}
    }
  }

  @Test void agentNameLanguageSurvivesSqlReloadAndResync() throws Exception {
    // Plan50: foaf:name is language-tagged in DCAT-AP; dcat_agent_name now stores the tag beside the text.
    try(SessionFactory factory=h2("plan50AgentName")) {
      EntityManager em=factory.createEntityManager();
      try {
        OdmsCatalogue node=catalogue(em);
        PersistenceManager manager=new PersistenceManager();Field field=PersistenceManager.class.getDeclaredField("em");field.setAccessible(true);
        EntityManager original=(EntityManager)field.get(manager);
        try {
          field.set(manager,em);
          for(int sync=0;sync<2;sync++) {
            FoafAgent creator=new FoafAgent(DCTerms.creator.getURI(),"https://example.org/creator",List.of("Ente","Agency"),"","","","",String.valueOf(node.getId()));
            creator.setNameLanguages(List.of("it","en"));
            node.setCreator(creator);manager.jpaUpdateOdmsCatalogue(node);em.clear();
          }
          FoafAgent reloaded=manager.jpaGetOdmsCatalogue(node.getId(),true).getCreator();
          Map<String,String> byLanguage=new HashMap<>();
          for(DcatProperty name:reloaded.getName())byLanguage.put(name.getLanguage(),name.getValue());
          assertEquals(Map.of("it","Ente","en","Agency"),byLanguage);
          assertEquals(2,count(em,"SELECT COUNT(*) FROM dcat_agent_name"),"resync replaces the names, no duplicates");
          // The JSON contract for existing clients is unchanged: "name" stays a list of strings.
          org.json.JSONObject json=new org.json.JSONObject(it.eng.idra.utils.GsonUtil.obj2Json(reloaded,FoafAgent.class));
          assertEquals("Ente",json.getJSONArray("name").getString(0));
          assertEquals("it",json.getJSONArray("nameLanguage").getString(0));
          FoafAgent fromJson=it.eng.idra.utils.GsonUtil.json2Obj(json.toString(),FoafAgent.class);
          assertEquals("en",fromJson.getName().get(1).getLanguage());
          // An agent without tags keeps the old JSON (no nameLanguage) and plain literals.
          FoafAgent plain=new FoafAgent(DCTerms.publisher.getURI(),null,List.of("Plain"),"","","","","1");
          assertFalse(new org.json.JSONObject(it.eng.idra.utils.GsonUtil.obj2Json(plain,FoafAgent.class)).has("nameLanguage"));
        } finally {field.set(manager,original);if(original!=null)original.close();}
      } finally {em.close();}
    }
  }

  @Test void agentNameLanguageInRdfAndSolr() throws Exception {
    Model m=ModelFactory.createDefaultModel();
    Resource agent=m.createResource("https://example.org/agent",FOAF.Agent)
        .addProperty(FOAF.name,m.createLiteral("Ente","it")).addProperty(FOAF.name,m.createLiteral("Agency","en"));
    Resource root=m.createResource("https://example.org/dataset",DCAT.Dataset).addProperty(DCTerms.title,"Dataset").addProperty(DCTerms.publisher,agent);
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",root);
    dataset.setMetadataEnvelope(null);
    Model out=ModelFactory.createDefaultModel();DcatApSerializer.addDatasetToModel(dataset,out);
    Resource exported=out.getResource("https://example.org/agent");
    assertTrue(out.contains(exported,FOAF.name,out.createLiteral("Ente","it")));
    assertTrue(out.contains(exported,FOAF.name,out.createLiteral("Agency","en")),"every name is read, not only the first");
    org.apache.solr.common.SolrDocument doc=new org.apache.solr.common.SolrDocument();
    dataset.getPublisher().toDoc(it.eng.idra.cache.CacheContentType.publisher).forEach(f -> doc.setField(f.getName(),f.getValue()));
    FoafAgent reloaded=FoafAgent.docToFoafAgent(doc,DCTerms.publisher.getURI(),"999999");
    Map<String,String> byLanguage=new HashMap<>();
    for(DcatProperty name:reloaded.getName())byLanguage.put(name.getLanguage(),name.getValue());
    assertEquals(Map.of("it","Ente","en","Agency"),byLanguage,"Solr keeps each name with its own tag");
  }

  @Test void failedBulkDeleteDoesNotLeaveItsTransactionOpen() throws Exception {
    try(SessionFactory factory=h2("plan50Rollback")) {
      EntityManager em=factory.createEntityManager();
      try {
        OdmsCatalogue node=catalogue(em);
        em.getTransaction().begin();
        em.createNativeQuery("INSERT INTO dcat_agent(agent_id,nodeID) VALUES('blocked-agent','"+node.getId()+"')").executeUpdate();
        em.createNativeQuery("CREATE TABLE plan50_blocker(agent_id VARCHAR(255), FOREIGN KEY(agent_id) REFERENCES dcat_agent(agent_id))").executeUpdate();
        em.createNativeQuery("INSERT INTO plan50_blocker VALUES('blocked-agent')").executeUpdate();
        em.getTransaction().commit();
        CachePersistenceManager cache=cache(em);
        // B5: the failing statement must roll the transaction back, not keep its locks until Idra restarts.
        assertThrows(RuntimeException.class,()->cache.jpaDeleteDatasetsByOdmsNode(node.getId()));
        assertFalse(em.getTransaction().isActive());
        em.getTransaction().begin();em.createNativeQuery("DROP TABLE plan50_blocker").executeUpdate();em.getTransaction().commit();
        cache.jpaDeleteDatasetsByOdmsNode(node.getId());
        assertEquals(0,count(em,"SELECT COUNT(*) FROM dcat_agent"));
      } finally {em.close();}
    }
  }

  private static OdmsCatalogue catalogue(EntityManager em) {
    OdmsCatalogue node=new OdmsCatalogue();node.setName("Creator fixture");node.setHost("https://example.org/creator-fixture");node.setActive(true);
    node.setFederationLevel(OdmsCatalogueFederationLevel.LEVEL_2);node.setNodeType(OdmsCatalogueType.DCATDUMP);node.setNodeState(OdmsCatalogueState.ONLINE);
    node.setHomepage("https://example.org");node.setPublisherName("Fixture");
    em.getTransaction().begin();em.persist(node);em.getTransaction().commit();em.clear();
    return node;
  }
  // The bulk delete also clears legacy MySQL tables/columns that the Hibernate-generated H2 schema lacks.
  private static final String[] LEGACY_TABLES={"dcat_applicable_legislation","dcat_dataset_hascurrentversion","dcat_dataset_next","dcat_dataset_prev",
    "dcat_dataset_previousversion","dcat_distribution_applicable_legislation","dcat_endpoint","dcat_hvd_category","dcat_location","dcat_periodoftime",
    "dcat_rights","dcat_series_first","dcat_series_last","dcat_series_legislation","dcat_series_next","dcat_series_prev","dcat_serves_dataset",
    "dcat_subject","dcat_theme","dcat_vcard","dcat_version_note","dcat_was_generated_by","dcat_agent_name","dcat_catalogue_record","dcat_checksum",
    "dcat_concept","dcat_concept_prefLabel","dcat_data_service","dcat_dataset_series","dcat_details","dcat_distribution_documentation",
    "dcat_distribution_language","dcat_documentation","dcat_hasVersion","dcat_isVersionOf","dcat_keyword","dcat_language","dcat_otherIdentifier",
    "dcat_provenance","dcat_record_language","dcat_relatedResource","dcat_relationship","dcat_sample","dcat_source",
    "dcat_standard_referencedocumentation","dcat_versionNotes","dcat_dataset"};
  private static void legacySchema(EntityManager em) {
    em.getTransaction().begin();
    for(String table:LEGACY_TABLES) {
      em.createNativeQuery("CREATE TABLE IF NOT EXISTS "+table+"(nodeID VARCHAR(255))").executeUpdate();
      for(String column:new String[]{"nodeID","dataset_id","distribution_id","dataset_series_id","catalogue_record_id","data_service_id","relationship_id"})
        em.createNativeQuery("ALTER TABLE "+table+" ADD COLUMN IF NOT EXISTS "+column+" VARCHAR(255)").executeUpdate();
    }
    em.getTransaction().commit();
  }
  private static CachePersistenceManager cache(EntityManager em) throws Exception {
    legacySchema(em);
    CachePersistenceManager cache=new CachePersistenceManager();Field field=CachePersistenceManager.class.getDeclaredField("em");field.setAccessible(true);field.set(cache,em);
    return cache;
  }
  private static int count(EntityManager em,String sql) {return ((Number)em.createNativeQuery(sql).getSingleResult()).intValue();}
  private SessionFactory h2(String name) throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:"+name+";DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");config.setProperty("hibernate.hbm2ddl.auto","create-drop");
    config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(var files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String cls=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> c=Class.forName(cls,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    return config.buildSessionFactory();
  }
}
