package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.cache.DatasetPersistence;
import java.nio.file.*;
import java.util.*;
import javax.persistence.Entity;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialPagePersistenceTest {
  private DcatDataset dataset(int id) throws Exception {
    Model m=ModelFactory.createDefaultModel();Resource r=m.createResource("https://example.org/page/"+id,DCAT.Dataset)
        .addProperty(DCTerms.identifier,"page-"+id).addProperty(DCTerms.title,"Dataset "+id);
    return new DcatApDeserializer().resourceToDataset("999998",r);
  }
  @Test void largeInitialPageFlushesAtCommitAndRejectsBothKindsOfDuplicateAtomically() throws Exception {
    Configuration c=new Configuration();c.getProperties().clear();
    c.setProperty("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    c.setProperty("hibernate.connection.driver_class","org.h2.Driver");c.setProperty("hibernate.connection.url","jdbc:h2:mem:initialPage;DB_CLOSE_DELAY=-1");
    c.setProperty("hibernate.dialect","org.hibernate.dialect.H2Dialect");c.setProperty("hibernate.hbm2ddl.auto","create-drop");c.setProperty("javax.persistence.validation.mode","none");c.setProperty("hibernate.generate_statistics","true");
    Path classes=Path.of("target/classes");
    try(var files=Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p->p.toString().endsWith(".class")&&!p.toString().contains("$")).forEach(p->{
        String name=classes.relativize(p).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
        try {Class<?> t=Class.forName(name,false,getClass().getClassLoader());if(t.isAnnotationPresent(Entity.class))c.addAnnotatedClass(t);}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
      });
    }
    try(SessionFactory factory=c.buildSessionFactory()) {
      DatasetPersistence writes=new DatasetPersistence(factory);List<DcatDataset> page=new ArrayList<>();
      for(int i=0;i<32;i++)page.add(dataset(i));
      factory.getStatistics().clear();assertEquals(32,writes.insertPage(page,d->{}).size());
      assertTrue(factory.getStatistics().getFlushCount()<=2,"Initial import must not flush the growing page for every identity lookup");
      assertThrows(javax.persistence.EntityExistsException.class,()->writes.insertPage(List.of(dataset(0)),d->{}));
      assertThrows(javax.persistence.EntityExistsException.class,()->writes.insertPage(List.of(dataset(40),dataset(40)),d->{}));
      assertThrows(IllegalStateException.class,()->writes.insertPage(List.of(dataset(41),dataset(42)),d->{throw new IllegalStateException("fixture preparation failed");}));
      try(var session=factory.openSession()) {assertEquals(32L,session.createQuery("SELECT COUNT(d) FROM DcatDataset d",Long.class).getSingleResult());}
    }
  }
}
