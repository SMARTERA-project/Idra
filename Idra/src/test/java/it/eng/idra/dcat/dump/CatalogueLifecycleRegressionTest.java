package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.*;
import it.eng.idra.cache.LodCacheManager;
import it.eng.idra.management.PersistenceManager;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueLifecycleRegressionTest {
  @Test void rdfImportKeepsAccessAndDownloadDistinctAndUnlabelledConceptUris() throws Exception {
    Model source=ModelFactory.createDefaultModel();Resource root=source.createResource("https://example.org/dataset",DCAT.Dataset)
        .addProperty(DCTerms.title,"Source dataset")
        .addProperty(DCAT.theme,source.createResource("https://example.org/theme/not-in-the-authority"))
        .addProperty(DCTerms.subject,source.createResource("https://example.org/subject/unlabelled"));
    Resource distribution=source.createResource("https://example.org/distribution",DCAT.Distribution)
        .addProperty(DCAT.accessURL,source.createResource("https://example.org/browser"));
    root.addProperty(DCAT.distribution,distribution);
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",root);
    assertTrue(org.apache.commons.lang.StringUtils.isBlank(dataset.getDistributions().get(0).getDownloadUrl().getValue()));
    Model actual=ModelFactory.createDefaultModel();DcatApSerializer.addDatasetToModel(dataset,actual);
    assertTrue(actual.contains(null,DCAT.accessURL,source.getResource("https://example.org/browser")));
    assertFalse(actual.contains(null,DCAT.downloadURL));
    assertTrue(actual.contains(null,DCAT.theme,source.getResource("https://example.org/theme/not-in-the-authority")));
    assertFalse(actual.contains(source.getResource("https://example.org/theme/not-in-the-authority"),SKOS.prefLabel));
    assertFalse(actual.contains(source.getResource("https://example.org/subject/unlabelled"),SKOS.prefLabel));
  }
  @Test void absentContactNameAndLicenseTypeDoNotGeneratePlaceholderAssertions() {
    Model model=ModelFactory.createDefaultModel();Resource dataset=model.createResource("https://example.org/dataset");
    VcardOrganization contact=new VcardOrganization(DCAT.contactPoint.getURI(),"https://example.org/contact","","","","","","999999");
    DcatApSerializer.serializeContactPoint(List.of(contact),model,dataset);
    assertFalse(model.contains(null,VCARD4.fn));
    DctLicenseDocument license=new DctLicenseDocument("https://example.org/license","","","","999999");
    DcatApSerializer.serializeLicense(license,model,dataset);
    assertFalse(model.contains(model.getResource(license.getUri()),DCTerms.type));
    assertEquals("",license.getType().getValue());
    license.getType().setValue("Attribution");
    DcatApSerializer.serializeLicense(license,model,dataset);
    assertEquals("Attribution",license.getType().getValue());
    assertTrue(model.contains(model.getResource(license.getUri()),DCTerms.type,
        model.getResource(DcatApSerializer.LICENSE_TYPE_BASE_URI+"Attribution")));
  }
  @Test void failedRdfDownloadDoesNotCloseAnUnopenedRepositoryConnection() throws Exception {
    com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(
        new java.net.InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/unavailable.rdf",exchange->{exchange.sendResponseHeaders(503,-1);exchange.close();});
    server.start();
    Map<String,String> settings=it.eng.idra.management.FederationCore.getSettings();
    String previous=settings.put("rdf_undefined_content_length","false");
    try {
      String url="http://127.0.0.1:"+server.getAddress().getPort()+"/unavailable.rdf";
      assertEquals(0,LodCacheManager.addRdf(url));
      assertEquals(0,LodCacheManager.addRdfList(List.of(url,url)));
    } finally {
      if(previous==null)settings.remove("rdf_undefined_content_length");
      else settings.put("rdf_undefined_content_length",previous);
      server.stop(0);
    }
  }
  @Test void cachedExportsCannotPublishRemovedOrInactiveCatalogues() {
    assertThrows(javax.ws.rs.NotFoundException.class,()->DcatApDumpManager.requirePublishedCatalogue(null));
    OdmsCatalogue node=new OdmsCatalogue();node.setActive(false);
    assertThrows(javax.ws.rs.NotFoundException.class,()->DcatApDumpManager.requirePublishedCatalogue(node));
    node.setActive(true);assertDoesNotThrow(()->DcatApDumpManager.requirePublishedCatalogue(node));
  }
  @Test void indexConvertsOffsetDatesWithoutChangingStoredMetadata() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    Resource root=source.createResource("https://example.org/offset-dataset",DCAT.Dataset)
        .addProperty(DCTerms.title,"Offset dates")
        .addProperty(DCTerms.issued,"2026-10-20T12:34:56+02:00")
        .addProperty(DCTerms.modified,"2026-10-20T10:34:56.123Z");
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",root);
    assertEquals("2026-10-20T10:34:56Z",parsed.toDoc().getFieldValue("releaseDate"));
    assertEquals("2026-10-20T10:34:56.123Z",parsed.toDoc().getFieldValue("updateDate"));
    assertEquals("2026-10-20T12:34:56+02:00",parsed.getReleaseDate().getValue());
  }
  @Test void readsChunkedBrokerEvidenceAndKeepsLegacyStringEvidence() throws Exception {
    String metadata=new org.json.JSONObject().put("version",1).put("sourceRdf","Città 東京 ".repeat(1000)).toString();
    String encoded=java.util.Base64.getEncoder().encodeToString(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    org.json.JSONArray chunks=new org.json.JSONArray();for(int i=0;i<encoded.length();i+=1024)chunks.put(encoded.substring(i,Math.min(i+1024,encoded.length())));
    org.json.JSONObject entity=new org.json.JSONObject().put("id","urn:test:dataset").put("type","Dataset");
    entity.put("metadataEnvelope",new org.json.JSONObject().put("type","Property").put("value",new org.json.JSONObject().put("encoding","base64-utf8-chunks-v1").put("chunks",chunks)));
    assertEquals(metadata,it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null).getMetadataEnvelope());
    java.io.ByteArrayOutputStream compressed=new java.io.ByteArrayOutputStream();
    try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(compressed)) {gzip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    entity.getJSONObject("metadataEnvelope").put("value",new org.json.JSONObject().put("encoding","gzip-base64-utf8-chunks-v1")
        .put("chunks",new org.json.JSONArray().put(java.util.Base64.getEncoder().encodeToString(compressed.toByteArray()))));
    assertEquals(metadata,it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null).getMetadataEnvelope());
    entity.getJSONObject("metadataEnvelope").put("value",metadata);
    assertEquals(metadata,it.eng.idra.utils.NgsiDcatReader.dataset(entity,"999999",id->null).getMetadataEnvelope());
  }
  @Test void missingByteSizeAndImplicitSpatialPredicateDoNotAbortMetadataCapture() throws Exception {
    Model model=ModelFactory.createDefaultModel();Resource distribution=model.createResource("https://example.org/dist");
    DcatApSerializer.serializeByteSize(null,model,distribution);
    DcatApSerializer.serializeByteSize(new DcatProperty(DCAT.byteSize,RDFS.Literal,""),model,distribution);
    assertFalse(distribution.hasProperty(DCAT.byteSize));
    DctLocation location=new DctLocation(null,"","","POINT(1 2)","999999","","");
    Resource dataset=model.createResource("https://example.org/dataset");
    DcatApSerializer.serializeSpatialCoverage(List.of(location),model,dataset);
    assertTrue(dataset.hasProperty(DCTerms.spatial));
    assertTrue(dataset.getPropertyResourceValue(DCTerms.spatial).hasProperty(ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry")));
    DctPeriodOfTime period=new DctPeriodOfTime();period.setStartDate(new DcatProperty(DCAT.startDate,XSD.date,"2026-01-01"));
    DcatApSerializer.serializeTemporalCoverage(List.of(period),model,dataset);
    assertTrue(dataset.hasProperty(DCTerms.temporal));
    DcatDistribution minimal=new DcatDistribution();minimal.setId("minimal");minimal.setNodeId("999999");minimal.setResourceUri("https://example.org/minimal");
    minimal.setAccessUrl(new DcatProperty(DCAT.accessURL,RDFS.Resource,"https://example.org/download"));
    Model source=ModelFactory.createDefaultModel();
    Resource root=source.createResource("https://example.org/minimal-dataset",DCAT.Dataset).addProperty(DCTerms.title,"Minimal");
    DcatDataset parsed=new DcatApDeserializer().resourceToDataset("999999",root);
    DcatMetadataEnvelope.recordTemporalValue(parsed,"Dépenses annuelles sur les marchés");
    parsed.getDistributions().add(minimal);
    DcatApSerializer.addDatasetToModel(parsed,model);
    assertTrue(model.getResource("https://example.org/minimal").hasProperty(DCAT.accessURL));
    assertTrue(model.contains(null,RDFS.label,"Dépenses annuelles sur les marchés"));
    assertTrue(model.listStatements().toList().stream().noneMatch(s->s.getPredicate().getURI().contains("Dépenses")));
  }
  @Test void technicalDumpContextIsAbsoluteAndHistoricalEndpointContextIsStable() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(123);node.setHost("a".repeat(64));
    assertEquals("urn:idra:catalogue:123",LodCacheManager.catalogueContext(node));
    node.setHost("https://example.org/catalog");
    assertEquals("https://example.org/catalog",LodCacheManager.catalogueContext(node));
  }
  @Test void catalogueWithoutImageCanBeReadWithDefaultAndInactiveQueries() throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:plan59Catalogue;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");
    config.setProperty("hibernate.hbm2ddl.auto","create-drop");
    config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(var files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    try(SessionFactory factory=config.buildSessionFactory()) {
      EntityManager em=factory.createEntityManager();
      try {
        OdmsCatalogue node=new OdmsCatalogue();node.setName("No image");node.setHost("https://example.org/no-image");node.setActive(false);
        node.setFederationLevel(OdmsCatalogueFederationLevel.LEVEL_2);node.setNodeType(OdmsCatalogueType.DCATDUMP);node.setNodeState(OdmsCatalogueState.OFFLINE);
        node.setHomepage("https://example.org");node.setPublisherName("Fixture");
        em.getTransaction().begin();em.persist(node);em.getTransaction().commit();em.clear();
        PersistenceManager manager=new PersistenceManager();Field field=PersistenceManager.class.getDeclaredField("em");field.setAccessible(true);
        EntityManager original=(EntityManager)field.get(manager);
        try {
          field.set(manager,em);
          assertEquals(node.getId(),manager.jpaGetOdmsCatalogue(node.getId(),true).getId());
          assertEquals(node.getId(),manager.jpaGetInactiveOdmsCatalogue(node.getId(),true).getId());
          assertEquals(1,manager.jpaGetAllInactiveOdmsCatalogue(true).size());
          DcatCatalogueRecord record=new DcatCatalogueRecord();record.setNodeId(String.valueOf(node.getId()));record.setResourceUri("https://example.org/record");
          // Mirror the real MySQL constraint: replacing a collection must not null its owner.
          em.getTransaction().begin();em.createNativeQuery("ALTER TABLE dcat_catalogue_record ALTER COLUMN nodeID SET NOT NULL").executeUpdate();em.getTransaction().commit();
          node.setRecord(new ArrayList<>(List.of(record)));
          manager.jpaUpdateOdmsCatalogue(node);
          em.clear();
          // Reuse the detached record after its generated identity has been assigned.
          manager.jpaUpdateOdmsCatalogue(node);
          em.clear();
          assertEquals(1,manager.jpaGetOdmsCatalogue(node.getId(),true).getRecord().size());
          // A refreshed RDF graph creates new bean instances for the same source URI.
          em.getTransaction().begin();em.createNativeQuery("ALTER TABLE dcat_catalogue_record ADD CONSTRAINT plan60_record_owner_unique UNIQUE(nodeID)").executeUpdate();em.getTransaction().commit();
          String recordId=node.getRecord().get(0).getCatalogueRecordId();
          DcatCatalogueRecord refreshed=new DcatCatalogueRecord();refreshed.setNodeId(String.valueOf(node.getId()));refreshed.setResourceUri("https://example.org/record");
          node.setRecord(new ArrayList<>(List.of(refreshed)));
          DcatDataService service=new DcatDataService();service.setNodeId(String.valueOf(node.getId()));service.setResourceUri("https://example.org/service");
          node.setService(new ArrayList<>(List.of(service)));manager.jpaUpdateOdmsCatalogue(node);em.clear();
          assertEquals(recordId,node.getRecord().get(0).getCatalogueRecordId());
          String serviceId=node.getService().get(0).getDataServiceId();
          DcatDataService refreshedService=new DcatDataService();refreshedService.setNodeId(String.valueOf(node.getId()));refreshedService.setResourceUri("https://example.org/service");
          node.setService(new ArrayList<>(List.of(refreshedService)));manager.jpaUpdateOdmsCatalogue(node);em.clear();
          assertEquals(serviceId,node.getService().get(0).getDataServiceId());
          DcatDatasetSeries series=new DcatDatasetSeries();series.setNodeId(String.valueOf(node.getId()));series.setResourceUri("https://example.org/standalone-series");
          series.setTitle(new ArrayList<>(List.of(new DcatDetails(null,null,null,null,String.valueOf(node.getId()),"Descrizione","Serie autonoma","it"))));
          node.setDatasetSeries(new ArrayList<>(List.of(series)));node.setStandaloneRdf("<https://example.org/standalone-series> a <http://www.w3.org/ns/dcat#DatasetSeries> .");
          manager.jpaUpdateOdmsCatalogue(node);em.clear();
          String seriesId=node.getDatasetSeries().get(0).getDatasetSeriesId();
          // State updates repeatedly merge the same detached child snapshots.
          manager.jpaUpdateOdmsCatalogue(node);em.clear();
          manager.jpaUpdateOdmsCatalogue(node);em.clear();
          assertEquals("Serie autonoma",manager.jpaGetOdmsCatalogue(node.getId(),true).getDatasetSeries().get(0).getTitle().get(0).getTitle());

          DcatDatasetSeries refreshedSeries=new DcatDatasetSeries();refreshedSeries.setNodeId(String.valueOf(node.getId()));refreshedSeries.setResourceUri("https://example.org/standalone-series");
          refreshedSeries.setTitle(new ArrayList<>(List.of(new DcatDetails(null,null,null,null,String.valueOf(node.getId()),"Descrizione","Serie autonoma","it"))));
          node.setDatasetSeries(new ArrayList<>(List.of(refreshedSeries)));manager.jpaUpdateOdmsCatalogue(node);em.clear();
          OdmsCatalogue reloaded=manager.jpaGetOdmsCatalogue(node.getId(),true);
          assertEquals(1,reloaded.getDatasetSeries().size());
          assertEquals(seriesId,reloaded.getDatasetSeries().get(0).getDatasetSeriesId());
          assertEquals("Serie autonoma",reloaded.getDatasetSeries().get(0).getTitle().get(0).getTitle());
          assertNull(reloaded.getDatasetSeries().get(0).getDatasetId());
          assertEquals(node.getStandaloneRdf(),reloaded.getStandaloneRdf());
          assertEquals(0,((Number)em.createNativeQuery("SELECT COUNT(*) FROM dcat_dataset").getSingleResult()).intValue());

          assertEquals(1,manager.jpaGetOdmsCatalogue(node.getId(),true).getRecord().size());
          assertEquals(1,manager.jpaGetOdmsCatalogue(node.getId(),true).getService().size());
          em.getTransaction().begin();em.createNativeQuery("ALTER TABLE dcat_catalogue_record DROP CONSTRAINT plan60_record_owner_unique").executeUpdate();em.getTransaction().commit();
          DcatCatalogueRecord first=node.getRecord().get(0);
          first.setTitle(new ArrayList<>(List.of(new DcatDetails(null,null,null,null,String.valueOf(node.getId()),"","Primo record","it"))));
          first.setApplicationProfile(new ArrayList<>(List.of(new DctStandard("https://example.org/profile/one","one","First profile","",List.of(),String.valueOf(node.getId())))));
          DcatCatalogueRecord second=new DcatCatalogueRecord();second.setNodeId(String.valueOf(node.getId()));second.setResourceUri("https://example.org/record/two");
          second.setTitle(new ArrayList<>(List.of(new DcatDetails(null,null,null,null,String.valueOf(node.getId()),"","Secondo record","it"))));
          second.setApplicationProfile(new ArrayList<>(List.of(new DctStandard("https://example.org/profile/two","two","Second profile","",List.of(),String.valueOf(node.getId())))));
          node.setRecord(new ArrayList<>(List.of(first,second)));
          manager.jpaUpdateOdmsCatalogue(node);em.clear();manager.jpaUpdateOdmsCatalogue(node);em.clear();
          OdmsCatalogue multi=manager.jpaGetOdmsCatalogue(node.getId(),true);
          assertEquals(2,multi.getRecord().size());
          for(DcatCatalogueRecord item:multi.getRecord()) {
            boolean isFirst=item.getResourceUri().equals("https://example.org/record");
            assertEquals(isFirst?"Primo record":"Secondo record",item.getTitle().get(0).getTitle());
            assertEquals(1,item.getApplicationProfile().size());
            assertEquals("https://example.org/profile/"+(isFirst?"one":"two"),item.getApplicationProfile().get(0).getUri());
          }
          assertEquals(2,((Number)em.createNativeQuery("SELECT COUNT(*) FROM dcat_standard WHERE catalogue_record_id IS NOT NULL").getSingleResult()).intValue());
          List<DcatCatalogueRecord> refreshedRecords=new ArrayList<>();
          for(DcatCatalogueRecord item:multi.getRecord()) {
            DcatCatalogueRecord fresh=new DcatCatalogueRecord();fresh.setResourceUri(item.getResourceUri());fresh.setNodeId(String.valueOf(node.getId()));
            fresh.setTitle(new ArrayList<>(List.of(new DcatDetails(null,null,null,null,String.valueOf(node.getId()),"","Titolo aggiornato","it"))));
            fresh.setApplicationProfile(new ArrayList<>(List.of(new DctStandard(item.getApplicationProfile().get(0).getUri(),"updated","Updated profile","",List.of(),String.valueOf(node.getId())))));
            refreshedRecords.add(fresh);
          }
          node.setRecord(refreshedRecords);manager.jpaUpdateOdmsCatalogue(node);em.clear();
          assertEquals(2,((Number)em.createNativeQuery("SELECT COUNT(*) FROM dcat_standard WHERE catalogue_record_id IS NOT NULL").getSingleResult()).intValue());
          assertEquals(2,((Number)em.createNativeQuery("SELECT COUNT(*) FROM dcat_standard").getSingleResult()).intValue());
          assertEquals(2,((Number)em.createNativeQuery("SELECT COUNT(*) FROM dcat_details WHERE catalogue_record_id IS NOT NULL").getSingleResult()).intValue());
          assertEquals(2,manager.jpaGetOdmsCatalogue(node.getId(),true).getRecord().size());
          // Cached catalogue instances can still belong to the read-side session.
          EntityManager reader=factory.createEntityManager();
          try {
            OdmsCatalogue cached=reader.find(OdmsCatalogue.class,node.getId());
            cached.getApplicableLegislation().size();
            manager.jpaUpdateOdmsCatalogue(cached);em.clear();
            assertEquals(2,manager.jpaGetOdmsCatalogue(node.getId(),true).getRecord().size());
          } finally {reader.close();}
        } finally {field.set(manager,original);if(original!=null)original.close();}
      } finally {em.close();}
    }
  }
}
