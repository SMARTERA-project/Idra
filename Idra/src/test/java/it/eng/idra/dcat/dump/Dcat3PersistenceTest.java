package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.*;
import java.nio.file.*;
import javax.persistence.Entity;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.hibernate.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Dcat3PersistenceTest {
  @Test void persistsHvdLegislationAndVocabularyUrisWithoutChangingValues() throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:hvdConformance;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");config.setProperty("hibernate.hbm2ddl.auto","create-drop");config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(java.util.stream.Stream<Path> files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try{Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,getClass().getResourceAsStream("/dcat3/hvd.ttl"),Lang.TURTLE);
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",source.getResource("https://example.org/dataset"));String id=dataset.getId();
    try(SessionFactory factory=config.buildSessionFactory()) {
      try(Session session=factory.openSession()){session.beginTransaction();session.persist(dataset);session.getTransaction().commit();}
      try(Session session=factory.openSession()) {
        DcatDataset loaded=session.get(DcatDataset.class,new DcatDatasetId(id,"999999"));
        assertEquals("http://data.europa.eu/bna/c_03ba8d92",loaded.getHVDCategory().get(0).getValue());
        assertEquals("http://data.europa.eu/eli/reg_impl/2023/138/oj",loaded.getApplicableLegislation().get(0).getValue());
        assertEquals(2,loaded.getDatasetDetails().size());
        DcatDistribution distribution=loaded.getDistributions().get(0);
        assertEquals("https://example.org/distribution",distribution.getResourceUri());
        assertEquals("http://publications.europa.eu/resource/authority/file-type/CSV",distribution.getFormat().getValue());
        assertEquals("https://www.iana.org/assignments/media-types/text/csv",distribution.getMediaType().getValue());
        assertEquals("http://data.europa.eu/eli/reg_impl/2023/138/oj",distribution.getApplicableLegislation().get(0).getValue());
      }
    }
  }
  @Test void persistsPublisherHomepageLongerThanVarchar255() throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:longHomepage;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");config.setProperty("hibernate.hbm2ddl.auto","create-drop");config.setProperty("javax.persistence.validation.mode","none");
    config.addAnnotatedClass(FoafAgent.class);
    String homepage="https://www.regione.umbria.it/protezione-civile/in-evidenza/-/asset_publisher/wxJKSsHXwm4O/content/covid-19-volontariato?"+"x".repeat(220);
    FoafAgent agent=new FoafAgent(org.apache.jena.vocabulary.DCTerms.publisher.getURI(),"https://example.org/agent",java.util.List.of("Regione Umbria"),"",homepage,"","","999999");
    try(SessionFactory factory=config.buildSessionFactory()) {
      try(Session session=factory.openSession()){session.beginTransaction();session.persist(agent);session.getTransaction().commit();}
      try(Session session=factory.openSession()) {
        assertEquals(homepage,session.get(FoafAgent.class,agent.getId()).getHomepage().getValue());
      }
    }
    assertTrue(homepage.length()>255);
  }
  @Test void persistsSourceTextsLongerThanVarchar255() throws Exception {
    // Lengths seen in S034/S015: provenance 15064, rights 7433, conforms_to 380, creator 270, spatial 262.
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:longTexts;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");config.setProperty("hibernate.hbm2ddl.auto","create-drop");config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(java.util.stream.Stream<Path> files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try{Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    String provenance="p".repeat(15064),rights="r".repeat(7433),standard="https://example.org/standard/"+"s".repeat(360),creator="c".repeat(270),agent="https://example.org/agent/"+"a".repeat(250),geometry="POLYGON(("+"1 1,".repeat(70)+"1 1))";
    String longIri="https://example.org/iri/"+"i".repeat(280);
    String ttl="@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. @prefix locn:<http://www.w3.org/ns/locn#>. @prefix foaf:<http://xmlns.com/foaf/0.1/>. @prefix vcard:<http://www.w3.org/2006/vcard/ns#>.\n"
      +"<https://example.org/long> a dcat:Dataset; dct:identifier \"long\"; dct:title \"Long\"@it; dct:provenance \""+provenance+"\";"
      +" dct:accessRights \""+rights+"\"; dct:conformsTo <"+standard+">;"
      +" dct:publisher <"+agent+">; dct:spatial [ a dct:Location; locn:geometry \""+geometry+"\" ];"
      +" dcat:theme <"+longIri+"/theme>; dcat:contactPoint [ a vcard:Organization; vcard:fn \"Contact\"; vcard:hasURL <"+longIri+"/contact> ];"
      +" dcat:distribution <https://example.org/long/d1>. <"+agent+"> a foaf:Agent; foaf:name \""+creator+"\"."
      +" <https://example.org/long/d1> a dcat:Distribution; dcat:accessURL <https://example.org/long/file.csv>; dct:license <"+longIri+"/license>.";
    try(SessionFactory factory=config.buildSessionFactory()) {
      OdmsCatalogue node=new OdmsCatalogue();node.setName("Long texts fixture");node.setHost("https://example.org/ckan");
      node.setHomepage("https://example.org");node.setPublisherName("Fixture");node.setNodeType(OdmsCatalogueType.CKAN);
      node.setNodeState(OdmsCatalogueState.ONLINE);node.setFederationLevel(OdmsCatalogueFederationLevel.LEVEL_3);
      try(Session session=factory.openSession()){session.beginTransaction();session.persist(node);session.getTransaction().commit();}
      String nodeId=String.valueOf(node.getId());
      Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,new java.io.ByteArrayInputStream(ttl.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Lang.TURTLE);
      DcatDataset dataset=new DcatApDeserializer().resourceToDataset(nodeId,source.getResource("https://example.org/long"));String id=dataset.getId();
      assertTrue(geometry.length()>255);assertFalse(dataset.getProvenance().isEmpty(),"provenance mapped");assertNotNull(dataset.getAccessRights(),"accessRights mapped");assertFalse(dataset.getConformsTo().isEmpty(),"conformsTo mapped");assertNotNull(dataset.getPublisher(),"publisher mapped");assertFalse(dataset.getSpatialCoverage().isEmpty(),"spatial mapped");
      try(Session session=factory.openSession()){session.beginTransaction();session.persist(dataset);session.getTransaction().commit();}
      try(Session session=factory.openSession()) {
        DcatDataset loaded=session.get(DcatDataset.class,new DcatDatasetId(id,nodeId));
        assertEquals(provenance,loaded.getProvenance().get(0).getValue());
        assertEquals(rights,loaded.getAccessRights().getValue());
        assertEquals(standard,loaded.getConformsTo().get(0).getUri());
        assertEquals(agent,loaded.getPublisher().getResourceUri());
        assertTrue(loaded.getTheme().stream().anyMatch(c->(longIri+"/theme").equals(c.getResourceUri())),"long theme IRI kept");
        assertEquals(longIri+"/contact",loaded.getContactPoint().get(0).getHasUrl().getValue());
        assertEquals(longIri+"/license",loaded.getDistributions().get(0).getLicense().getUri());
        assertEquals(geometry,loaded.getSpatialCoverage().get(0).getGeometry().getValue());
      }
    }
  }
  @Test void catalogueCoverageDoesNotIncludeDatasetCoverage() throws Exception {
    Configuration config=new Configuration();config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:catalogueCoverage;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");
    config.setProperty("hibernate.hbm2ddl.auto","create-drop");
    config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(java.util.stream.Stream<Path> files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    try(SessionFactory factory=config.buildSessionFactory()) {
      OdmsCatalogue node=new OdmsCatalogue();node.setName("Coverage fixture");node.setHost("https://example.org/csw");
      node.setHomepage("https://example.org");node.setPublisherName("Fixture");node.setNodeType(OdmsCatalogueType.GEONETWORK_ISO19139);
      node.setNodeState(OdmsCatalogueState.ONLINE);node.setFederationLevel(OdmsCatalogueFederationLevel.LEVEL_2);
      try(Session session=factory.openSession()) {
        session.beginTransaction();session.persist(node);session.getTransaction().commit();
      }
      String nodeId=String.valueOf(node.getId());
      Model source=ModelFactory.createDefaultModel();RDFDataMgr.read(source,getClass().getResourceAsStream("/dcat3/series.ttl"),Lang.TURTLE);
      DcatDataset dataset=new DcatApDeserializer().resourceToDataset(nodeId,source.getResource("https://example.org/dataset/v2"));
      DctLocation location=new DctLocation();location.setNodeId(nodeId);
      dataset.getSpatialCoverage().add(location);
      DctPeriodOfTime period=new DctPeriodOfTime();period.setNodeId(nodeId);
      dataset.getTemporalCoverage().add(period);
      try(Session session=factory.openSession()) {
        session.beginTransaction();session.persist(dataset);session.getTransaction().commit();
      }
      try(Session session=factory.openSession()) {
        OdmsCatalogue loaded=session.get(OdmsCatalogue.class,node.getId());
        assertTrue(loaded.getGeographicalCoverage().isEmpty(),"Dataset locations must not belong to the catalogue");
        assertTrue(loaded.getTemporalCoverage().isEmpty(),"Dataset periods must not belong to the catalogue");
      }
    }
  }
  @Test void persistsSeriesIdentityAndVersionLinksWithoutMixingMemberDetails() throws Exception {
    Configuration config=new Configuration();
    config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
    config.setProperty("hibernate.connection.url","jdbc:h2:mem:dcat3;DB_CLOSE_DELAY=-1");
    config.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");
    config.setProperty("hibernate.hbm2ddl.auto","create-drop");
    config.setProperty("javax.persistence.validation.mode","none");
    Path classes=Path.of("target/classes");
    try(java.util.stream.Stream<Path> files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class") && !p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> c=Class.forName(name,false,getClass().getClassLoader());if(c.isAnnotationPresent(Entity.class))config.addAnnotatedClass(c);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    try(SessionFactory factory=config.buildSessionFactory()) {
      Model source=ModelFactory.createDefaultModel();
      RDFDataMgr.read(source,getClass().getResourceAsStream("/dcat3/series.ttl"),Lang.TURTLE);
      DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",source.getResource("https://example.org/dataset/v2"));
      String id=dataset.getId();
      try(Session session=factory.openSession()) {
        session.beginTransaction();session.persist(dataset);session.getTransaction().commit();
      }
      try(Session session=factory.openSession()) {
        DcatDataset loaded=session.get(DcatDataset.class,new DcatDatasetId(id,"999999"));
        assertEquals("https://example.org/dataset/v2",loaded.getResourceUri());
        assertEquals("https://example.org/dataset/v1",loaded.getPreviousVersion().get(0));
        assertEquals("https://example.org/series",loaded.getInSeries().get(0).getResourceUri());
        assertEquals(2,loaded.getDatasetDetails().size());
        assertEquals(2,loaded.getInSeries().get(0).getTitle().size());
      }
    }
  }
}
