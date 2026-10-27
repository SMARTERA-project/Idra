package it.eng.idra.management;

import it.eng.idra.api.AdministrationApi;
import it.eng.idra.beans.odms.*;
import it.eng.idra.beans.orion.OrionCatalogueConfiguration;
import it.eng.idra.beans.sparql.SparqlCatalogueConfiguration;
import it.eng.idra.utils.GsonUtil;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueRegistrationTest {
  Field registry;
  Object saved;
  @BeforeEach void isolateRegistry() throws Exception {
    registry = OdmsManager.class.getDeclaredField("federatedNodes");
    registry.setAccessible(true); saved = registry.get(null);
    registry.set(null, new ConcurrentHashMap<Integer, OdmsCatalogue>());
  }
  @AfterEach void restoreRegistry() throws Exception { registry.set(null, saved); }

  OdmsCatalogue node(int id, String name, String host, OdmsCatalogueType type) {
    OdmsCatalogue n = new OdmsCatalogue(); n.setId(id); n.setName(name);
    n.setHost(host); n.setNodeType(type); return n;
  }
  @SuppressWarnings("unchecked") void remember(OdmsCatalogue n) throws Exception {
    ((ConcurrentHashMap<Integer, OdmsCatalogue>) registry.get(null)).put(n.getId(), n);
  }
  String json(String type, String extra) {
    return "{\"name\":\"QA\",\"host\":\"https://example.org/catalog\","
        + "\"nodeType\":\"" + type + "\",\"federationLevel\":\"LEVEL_4\","
        + "\"publisherName\":\"QA\",\"isActive\":false" + extra + "}";
  }
  OdmsCatalogue parse(String value) throws Exception { return GsonUtil.json2Obj(value, GsonUtil.nodeType); }

  @Test void sameDisplayNameFindsBothCataloguesAndUnknownNameFindsNone() throws Exception {
    remember(node(20, "Marche", "https://one.example", OdmsCatalogueType.CKAN));
    remember(node(30, "Marche", "https://two.example", OdmsCatalogueType.CKAN));
    assertEquals(List.of(20, 30), OdmsManager.getOdmsCatalogueIdsByName("Marche"));
    assertTrue(OdmsManager.getOdmsCatalogueIdsByName("unknown").isEmpty());
    assertFalse(OdmsManager.isDuplicateEndpoint(node(0, "Marche", "https://three.example", OdmsCatalogueType.CKAN)));
    assertTrue(OdmsManager.isDuplicateEndpoint(node(0, "different name", "https://one.example/", OdmsCatalogueType.CKAN)));
  }
  @Test void dumpRetryUsesFullCaseSensitiveUrlNotDisplayNameOrCommonHost() throws Exception {
    OdmsCatalogue stored = node(20, "Dump", "technical-id", OdmsCatalogueType.DCATDUMP);
    stored.setDumpUrl("https://example.org/sparql?query=CodeA"); remember(stored);
    OdmsCatalogue candidate = node(0, "Dump", "", OdmsCatalogueType.DCATDUMP);
    candidate.setDumpUrl(stored.getDumpUrl()); assertTrue(OdmsManager.isDuplicateEndpoint(candidate));
    candidate.setDumpUrl("https://example.org/sparql?query=CodeB"); assertFalse(OdmsManager.isDuplicateEndpoint(candidate));
    candidate.setDumpUrl("https://example.org/sparql?query=codeA"); assertFalse(OdmsManager.isDuplicateEndpoint(candidate));
    candidate.setDumpUrl(null); assertFalse(OdmsManager.isDuplicateEndpoint(candidate));
  }
  @Test void separateDumpRegistrationsWithIdenticalLabelsReceiveDifferentTechnicalHosts() {
    OdmsCatalogue n = node(0, "same label", "", OdmsCatalogueType.DCATDUMP);
    String first = OdmsManager.createUniqueString(n), second = OdmsManager.createUniqueString(n);
    assertTrue(first.matches("[a-f0-9]{64}")); assertNotEquals(first, second);
  }
  @Test void missingHomepageDefaultsToProvidedHostAndExplicitHomepageIsPreserved() throws Exception {
    assertEquals("https://example.org/catalog", parse(json("CKAN", "")).getHomepage());
    assertEquals("https://publisher.example", parse(json("CKAN", ",\"homepage\":\"https://publisher.example\"")).getHomepage());
    assertThrows(Exception.class, () -> parse(json("CKAN", "").replace("\"name\":\"QA\",", "")));
  }
  @Test void typedOrionConfigurationSurvivesWithoutAnInlineDump() throws Exception {
    OdmsCatalogue n = parse(json("ORION", ",\"additionalConfig\":{\"type\":\"ORION\","
        + "\"isAuthenticated\":true,\"ngsild\":true,\"clientID\":\"qa\","
        + "\"clientSecret\":\"fixture\",\"oauth2Endpoint\":\"https://example.org/token\","
        + "\"orionDatasetFilePath\":\"/remote/server/dump\"}"));
    OrionCatalogueConfiguration c = (OrionCatalogueConfiguration) n.getAdditionalConfig();
    assertNotNull(c); assertTrue(c.isNgsild()); assertTrue(c.isAuthenticated());
    assertEquals("qa", c.getClientId()); assertEquals("/remote/server/dump", c.getOrionDatasetFilePath());
    assertTrue(c.getOrionDatasetDumpString().isEmpty());
  }
  @Test void remoteFilePathCannotStandInForUploadedOrionDump() throws Exception {
    String input = json("ORION", ",\"additionalConfig\":{\"type\":\"ORION\","
        + "\"isAuthenticated\":false,\"orionDatasetFilePath\":\"/remote/server/dump\"}");
    AdministrationApi api = new AdministrationApi();
    for (ByteArrayInputStream upload : new ByteArrayInputStream[]{null, new ByteArrayInputStream(new byte[0])}) {
      try (Response r = api.registerOdmsCatalogue(upload, null, input)) {
        assertEquals(400, r.getStatus()); assertTrue(r.getEntity().toString().contains("dump"));
      }
    }
    assertTrue(OdmsManager.getOdmsCatalogueIdsByName("QA").isEmpty());
  }
  @Test void typedSparqlConfigurationWithoutContentIsReportedAsBadRequest() throws Exception {
    String input = json("SPARQL", ",\"additionalConfig\":{\"type\":\"SPARQL\",\"sparqlDatasetFilePath\":\"/remote/dump\"}");
    assertTrue(parse(input).getAdditionalConfig() instanceof SparqlCatalogueConfiguration);
    try (Response r = new AdministrationApi().registerOdmsCatalogue(null, null, input)) {
      assertEquals(400, r.getStatus());
    }
  }
}
