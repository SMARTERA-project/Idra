package it.eng.idra.cache;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.dcat.dump.DcatApDeserializer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Requires the isolated fixture DB, never connects to the application's original DB. */
class MysqlConcurrencyTest {
  static SessionFactory factory;
  static final String URL = "jdbc:mysql://127.0.0.1:13345/plan45_fixture?useSSL=false&allowPublicKeyRetrieval=true";

  @BeforeAll static void setup() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("plan45.mysql"), "Run with -Dplan45.mysql=true against the isolated copy");
    Configuration config = new Configuration(); config.getProperties().clear();
    config.setProperty("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    config.setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
    config.setProperty("hibernate.connection.url", URL);
    config.setProperty("hibernate.connection.username", "root");
    config.setProperty("hibernate.connection.password", "plan45-isolated-only");
    config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
    config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    config.setProperty("javax.persistence.validation.mode", "none");
    config.setProperty("hibernate.connection.pool_size", "8");
    Path classes = Path.of("target/classes");
    try (java.util.stream.Stream<Path> files = Files.walk(classes.resolve("it/eng/idra/beans"))) {
      files.filter(p -> p.toString().endsWith(".class") && !p.toString().contains("$")).forEach(p -> {
        String name = classes.relativize(p).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
        try { Class<?> c = Class.forName(name, false, MysqlConcurrencyTest.class.getClassLoader());
          if (c.isAnnotationPresent(Entity.class)) config.addAnnotatedClass(c);
        } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
      });
    }
    factory = config.buildSessionFactory();
    try (Connection c = connect(); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE plan45_retry (id INT PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB");
      s.execute("INSERT INTO plan45_retry VALUES (1,0),(2,0)");
    }
  }
  @AfterAll static void cleanup() throws Exception {
    if (factory != null) {
      try (Connection c = connect(); Statement s = c.createStatement()) { s.execute("DROP TABLE plan45_retry"); }
      finally { factory.close(); }
    }
  }
  static Connection connect() throws SQLException { return DriverManager.getConnection(URL, "root", "plan45-isolated-only"); }

  @Test void immediateAndBatchApisUseCommittedDbAndScopedReconciliation(
      @org.junit.jupiter.api.io.TempDir Path indexHome) throws Exception {
    var factoryField = CachePersistenceManager.class.getDeclaredField("emf"); factoryField.setAccessible(true);
    var serverField = MetadataCacheManager.class.getDeclaredField("server"); serverField.setAccessible(true);
    Object oldFactory = factoryField.get(null), oldServer = serverField.get(null);
    try (var index = SolrSyncBenchmarkTest.open(indexHome.resolve("solr"))) {
      factoryField.set(null,factory); serverField.set(null,index);
      DcatDataset original = dataset("999984");
      MetadataCacheManager.addDataset(original);
      assertEquals(1,SolrSyncBenchmarkTest.count(index,"nodeID:999984 AND content_type:dataset"));
      DcatDataset committed = read(original);
      DcatDataset indexed = MetadataCacheManager.getDatasetByIdentifier(999984,original.getIdentifier().getValue());
      assertEquals(new ArrayList<>(committed.getPreviousVersion()),new ArrayList<>(indexed.getPreviousVersion()));
      assertEquals(committed.getInSeries().get(0).getResourceUri(),indexed.getInSeries().get(0).getResourceUri());
      try (SyncIndexBatch batch = new SyncIndexBatch(index,999984,List.of(committed),1000)) {
        DcatDataset replacement = dataset("999984"); replacement.getTitle().setValue("batch changed title");
        MetadataCacheManager.updateDataset(999984,replacement,batch);
        assertEquals("batch changed title",read(original).getTitle().getValue());
        assertEquals("batch changed title",batch.find(original.getIdentifier().getValue()).getTitle().getValue());
        batch.flush();
        assertEquals(1,SolrSyncBenchmarkTest.count(index,"nodeID:999984 AND content_type:distribution"));
        MetadataCacheManager.deleteDataset(999984,replacement,batch);
        assertNull(read(original));
        DcatDataset foreign = dataset("999983"); MetadataCacheManager.addDataset(foreign);
        assertEquals(1,SolrSyncBenchmarkTest.count(index,"nodeID:999983 AND content_type:dataset"));
      }
      assertEquals(0,SolrSyncBenchmarkTest.count(index,"nodeID:999984"));
      // Index stale/missing after a permanent failure: reconcile from the authoritative DB.
      DcatDataset persisted = new DatasetPersistence(factory).insert(dataset("999984"));
      MetadataCacheManager.reconcileDatasetIndex(999984,List.of(original.getId(),persisted.getId()));
      assertEquals(1,SolrSyncBenchmarkTest.count(index,"nodeID:999984 AND content_type:dataset"));
      assertEquals(1,SolrSyncBenchmarkTest.count(index,"nodeID:999983 AND content_type:dataset"));
      assertEquals(persisted.getId(),MetadataCacheManager.getDatasetByIdentifier(999984,
          persisted.getIdentifier().getValue()).getId());
    } finally { factoryField.set(null,oldFactory); serverField.set(null,oldServer); }
  }
  static DcatDataset dataset(String node) {
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, MysqlConcurrencyTest.class.getResourceAsStream("/dcat3/series.ttl"), Lang.TURTLE);
    model.read(new java.io.StringReader("<https://example.org/dataset/v2> <http://www.w3.org/ns/dcat#distribution> <https://example.org/dist> . "
        + "<https://example.org/dist> a <http://www.w3.org/ns/dcat#Distribution>; <http://www.w3.org/ns/dcat#accessURL> <https://example.org/data.csv> ."), null, "TURTLE");
    try { return new DcatApDeserializer().resourceToDataset(node, model.getResource("https://example.org/dataset/v2")); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  static DcatDataset read(DcatDataset dataset) {
    EntityManager em = factory.createEntityManager();
    try { return em.find(DcatDataset.class, new DcatDatasetId(dataset.getId(), dataset.getNodeId())); }
    finally { em.close(); }
  }

  @Test void sourceEvidenceSurvivesRealMysqlInsertAndAtomicReplacement() {
    DatasetPersistence writes=new DatasetPersistence(factory);DcatDataset source=dataset("999982");
    assertNotNull(source.getMetadataEnvelope());
    DcatDataset committed=writes.insert(source);
    try {
      assertEquals(source.getMetadataEnvelope(),read(committed).getMetadataEnvelope());
      DcatDataset replacement=dataset("999982");replacement.setId(committed.getId());
      DcatDataset changed=writes.replace(replacement);
      assertEquals(replacement.getMetadataEnvelope(),read(changed).getMetadataEnvelope());
    } finally {writes.delete(committed);}
  }

  @Test void oldTwoCommitReplacementCanLoseDataset() {
    DatasetPersistence writes = new DatasetPersistence(factory);
    DcatDataset original = writes.insert(dataset("999990"));
    EntityManager em = factory.createEntityManager();
    try {
      em.getTransaction().begin();
      em.remove(em.find(DcatDataset.class, new DcatDatasetId(original.getId(), original.getNodeId())));
      em.getTransaction().commit();
      em.clear();
      DcatDataset invalid = dataset(original.getNodeId()); invalid.setId(original.getId());
      invalid.setResourceUri("x".repeat(3000));
      em.getTransaction().begin();
      assertThrows(RuntimeException.class, () -> { em.persist(invalid); em.getTransaction().commit(); });
      if (em.getTransaction().isActive()) em.getTransaction().rollback();
      assertNull(read(original), "Old delete commit survives failed insert");
    } finally { em.close(); }
  }

  @Test void failedAtomicReplacementPreservesDatasetAndSeriesThenRecovers() {
    DatasetPersistence writes = new DatasetPersistence(factory);
    DcatDataset original = writes.insert(dataset("999991"));
    DcatDataset invalid = dataset(original.getNodeId()); invalid.setId(original.getId());
    invalid.setResourceUri("x".repeat(3000));
    assertThrows(RuntimeException.class, () -> writes.replace(invalid));
    DcatDataset preserved = read(original);
    assertNotNull(preserved);
    assertEquals(original.getResourceUri(), preserved.getResourceUri());
    assertEquals(original.getInSeries().get(0).getResourceUri(), preserved.getInSeries().get(0).getResourceUri());
    DcatDataset replacement = dataset(original.getNodeId()); replacement.setId(original.getId());
    DcatDataset committed = writes.replace(replacement);
    assertEquals(original.getId(), committed.getId());
    assertEquals(2, read(original).getDatasetDetails().size());
    assertEquals(new ArrayList<>(original.getPreviousVersion()), new ArrayList<>(committed.getPreviousVersion()));
    assertEquals(1, committed.getDistributions().size());
    assertNotNull(committed.getDistributions().get(0).getId());
    assertEquals(committed.getDistributions().get(0).getId(), read(committed).getDistributions().get(0).getId());
    assertEquals(replacement.getTitle().getUri(), committed.getTitle().getUri(), "Snapshot preserves DCAT properties");
    assertDoesNotThrow(() -> committed.toDoc());
    writes.delete(committed); writes.delete(committed);
    assertNull(read(committed));
  }

  @Test void actualMysqlDeadlockRecoversWithFreshContextAndNoPartialWrites() throws Exception {
    CountDownLatch bothLocked = new CountDownLatch(2);
    AtomicInteger attempts = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int first : new int[] {1, 2}) results.add(pool.submit(() -> {
        AtomicInteger ownAttempts = new AtomicInteger();
        DatabaseTransaction.execute(factory, em -> {
          attempts.incrementAndGet();
          em.createNativeQuery("UPDATE plan45_retry SET value=value+1 WHERE id=" + first).executeUpdate();
          if (ownAttempts.getAndIncrement() == 0) {
            bothLocked.countDown();
            try { if (!bothLocked.await(10, TimeUnit.SECONDS)) throw new AssertionError("Barrier timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
          }
          em.createNativeQuery("UPDATE plan45_retry SET value=value+1 WHERE id=" + (3-first)).executeUpdate();
          return null;
        });
      }));
      for (Future<?> result : results) result.get(20, TimeUnit.SECONDS);
      assertEquals(3, attempts.get(), "One real deadlock, two commits, one retry");
      try (Connection c=connect(); Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT SUM(value) FROM plan45_retry")) {
        rs.next(); assertEquals(4, rs.getInt(1), "Rolled-back increments must not survive");
      }
    } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS)); }
  }

  @Test void retryIsBoundedPermanentAndTimeoutErrorsPropagateAndContextsClose() {
    for (int code : new int[] {1213, 1205, 1062}) {
      AtomicInteger calls=new AtomicInteger(); List<EntityManager> contexts=new ArrayList<>();
      RuntimeException failure = new IllegalStateException(new SQLException("fixture", code==1213?"40001":"HY000", code));
      RuntimeException thrown = assertThrows(RuntimeException.class, () -> DatabaseTransaction.execute(factory, em -> {
        contexts.add(em); calls.incrementAndGet();
        em.createNativeQuery("UPDATE plan45_retry SET value=value+100 WHERE id=1").executeUpdate();
        throw failure;
      }));
      assertSame(failure, thrown);
      assertEquals(code==1213?3:1, calls.get());
      assertEquals(contexts.size(), new HashSet<>(contexts).size());
      assertTrue(contexts.stream().noneMatch(EntityManager::isOpen));
    }
    try (Connection c=connect(); Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT MAX(value) FROM plan45_retry")) {
      rs.next(); assertTrue(rs.getInt(1)<100, "Every failed attempt rolled back");
    } catch (SQLException e) { throw new IllegalStateException(e); }
  }

  @Test void differentCataloguesReplaceConcurrentlyWithStableDatasetIdentity() throws Exception {
    DatasetPersistence writes = new DatasetPersistence(factory);
    DcatDataset torino = writes.insert(dataset("999992")), paris = writes.insert(dataset("999993"));
    CyclicBarrier barrier=new CyclicBarrier(2); ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> jobs=new ArrayList<>();
      for (DcatDataset old : Arrays.asList(torino,paris)) jobs.add(pool.submit(() -> {
        try { barrier.await(10,TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException(e); }
        DcatDataset update=dataset(old.getNodeId());update.setId(old.getId());
        for(int i=0;i<3;i++) writes.replace(update);
      }));
      for(Future<?> job:jobs)job.get(30,TimeUnit.SECONDS);
      assertNotNull(read(torino));assertNotNull(read(paris));
      assertEquals(2,read(torino).getDatasetDetails().size());
      assertEquals(2,read(paris).getInSeries().get(0).getTitle().size());
    } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));writes.delete(torino);writes.delete(paris);}
  }

  @Test void initialPageRetriesFromPristineDataAndPermanentFailureRollsBackWholePage() {
    DatasetPersistence writes = new DatasetPersistence(factory);
    DcatDataset source = dataset("999994");
    AtomicInteger preparations = new AtomicInteger();
    List<DcatDataset> page = writes.insertPage(Arrays.asList(source), attempt -> {
      if(preparations.getAndIncrement()==0) throw new IllegalStateException(new SQLException("fixture","40001",1213));
    });
    assertEquals(2,preparations.get());
    assertNotNull(read(page.get(0)));
    assertNull(source.getDistributions().get(0).getId(),"Failed sessions never mutate connector objects");
    assertNotNull(page.get(0).getDistributions().get(0).getId());
    writes.delete(page.get(0));
    DcatDataset good=dataset("999995"), bad=dataset("999995");
    bad.setResourceUri("x".repeat(3000));
    assertThrows(RuntimeException.class,()->writes.insertPage(Arrays.asList(good,bad), d->{}));
    assertNull(read(good));assertNull(read(bad));
  }

  @Test void snapshotPreservesOrionConfigurationSubtypeAndGeneratedIds() {
    DatasetPersistence writes = new DatasetPersistence(factory);
    DcatDataset source = dataset("999996");
    it.eng.idra.beans.orion.OrionDistributionConfig config = new it.eng.idra.beans.orion.OrionDistributionConfig(
        "type=fixture", "service", "/path", "https://example.org/context", source.getNodeId());
    source.getDistributions().get(0).setDistributionAdditionalConfig(config);
    DcatDataset committed = writes.insertPage(Arrays.asList(source), d -> {
      assertTrue(d.getDistributions().get(0).getDistributionAdditionalConfig() instanceof it.eng.idra.beans.orion.OrionDistributionConfig);
      assertNotNull(d.getDistributions().get(0).getDistributionAdditionalConfig().getId());
    }).get(0);
    it.eng.idra.beans.orion.OrionDistributionConfig saved = (it.eng.idra.beans.orion.OrionDistributionConfig)
        read(committed).getDistributions().get(0).getDistributionAdditionalConfig();
    assertEquals(config.getFiwareService(),saved.getFiwareService());
    assertEquals(config.getContext(),saved.getContext());
    assertEquals(source.getNodeId(),saved.getNodeId());
    assertNull(config.getId());
    writes.delete(committed);
  }

  @Test void staleSearchCannotInsertAnotherDatasetWithTheSamePersistedIdentity() {
    DatasetPersistence writes=new DatasetPersistence(factory);
    DcatDataset existing=writes.insert(dataset("999997"));
    try {
      assertThrows(javax.persistence.EntityExistsException.class,()->writes.insert(dataset("999997")));
      assertNotNull(read(existing));
      EntityManager em=factory.createEntityManager();
      try {assertEquals(1L,em.createQuery("SELECT COUNT(d) FROM DcatDataset d WHERE d.nodeId='999997'",Long.class).getSingleResult());}
      finally {em.close();}
    } finally {writes.delete(existing);}
  }
}
