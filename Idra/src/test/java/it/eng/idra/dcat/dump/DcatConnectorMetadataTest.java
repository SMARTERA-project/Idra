package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.connectors.*;
import it.eng.idra.utils.*;
import java.util.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatConnectorMetadataTest {
  @Test void ckanHashUsesDeclaredAlgorithmAndDoesNotInventSha1ForBareDigests() throws Exception {
    CkanConnector connector=new CkanConnector(node());
    var method=CkanConnector.class.getDeclaredMethod("resourceToDcat",org.ckan.Resource.class,String.class,DctLicenseDocument.class);method.setAccessible(true);
    String digest="7b5c49776c2c43eebed5958c1338e85e";
    for(String hash:List.of("md5:"+digest,digest)) {
      JSONObject raw=new JSONObject().put("id","checksum-resource").put("name","CSV")
          .put("url","https://example.org/file.csv").put("format","CSV").put("hash",hash);
      org.ckan.Resource source=org.ckan.Client.gson.fromJson(raw.toString(),org.ckan.Resource.class);
      DcatDistribution actual=(DcatDistribution)method.invoke(connector,source,"https://example.org/dataset",null);
      assertEquals(digest,actual.getChecksum().getChecksumValue().getValue());
      assertEquals(hash.startsWith("md5:")?"checksumAlgorithm_md5":"",actual.getChecksum().getAlgorithm().getValue());
      assertEquals(hash,source.getHash());
    }
  }
  private OdmsCatalogue node() {OdmsCatalogue n=new OdmsCatalogue();n.setId(999999);n.setHost("https://example.org");return n;}
  private JSONObject flat() {
    return new JSONObject("{\"id\":\"fixture\",\"identifier\":\"fixture\",\"guid\":\"fixture\",\"title\":\"Budget\",\"description\":\"Annual budget\",\"inSeries\":[{\"resourceUri\":\"https://example.org/series\",\"title\":\"Budgets\"}],\"previousVersion\":[\"https://example.org/v1\"],\"hvdCategory\":[\"https://example.org/hvd\"]}");
  }
  @Test void jsonConnectorsPreserveProvidedExtensions() throws Exception {
    OdmsCatalogue node=node();
    List<IodmsConnector> connectors=Arrays.asList(new DkanConnector(node),new SocrataConnector(node),new JunarConnector(node),new SparqlConnector(node),new OrionConnector(node));
    for(IodmsConnector connector:connectors) {
      JSONObject input=flat();
      input.put("distributions",new JSONArray().put(new JSONObject().put("title","CSV").put("accessURL","https://example.org/data")
          .put("distributionAdditionalConfig",new JSONObject().put("query","CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }").put("formats","[]"))));
      DcatDataset dataset=connector.datasetToDcat(input,node);
      assertEquals("https://example.org/series",dataset.getInSeries().get(0).getResourceUri(),connector.getClass().getName());
      assertEquals("https://example.org/v1",dataset.getPreviousVersion().get(0));
      assertEquals("https://example.org/hvd",dataset.getHVDCategory().get(0).getValue());
    }
  }
  @Test void ckanSdkRetainsTopLevelAndExtraMetadataBeyondItsModel() throws Exception {
    // Initializes the SDK adapter installed by CkanConnector.
    new CkanConnector(node());
    JSONObject source=flat().put("name","fixture").put("isopen",true).put("notes","Budget");
    source.put("extras",new JSONArray().put(new JSONObject().put("key","hasCurrentVersion").put("value","[\"https://example.org/v2\"]")));
    org.ckan.Dataset bean=org.ckan.Client.gson.fromJson(source.toString(),org.ckan.Dataset.class);
    DcatDataset target=new DcatDataset();target.setId("id");target.setNodeId("999999");
    DcatJsonMetadata.apply(target,DcatJsonMetadata.source(bean));
    assertEquals("https://example.org/series",target.getInSeries().get(0).getResourceUri());
    assertEquals("https://example.org/v2",target.getHasCurrentVersion().get(0));
  }
  @Test void ckanSdkAcceptsStringEncodedAccessServices() throws Exception {
    // S059 (Reggio Emilia) emits access_services:"" on one resource; the whole package_search failed before.
    new CkanConnector(node());
    JSONArray resources=new JSONArray()
      .put(new JSONObject().put("id","r1").put("url","https://example.org/a.csv").put("access_services",""))
      .put(new JSONObject().put("id","r2").put("url","https://example.org/b.csv").put("access_services","[{\"title\":\"WMS\",\"endpoint_url\":[\"https://example.org/wms\"]}]"))
      .put(new JSONObject().put("id","r3").put("url","https://example.org/c.csv").put("access_services",new JSONArray().put(new JSONObject().put("title","API"))))
      .put(new JSONObject().put("id","r4").put("url","https://example.org/d.csv").put("access_services","[not json"));
    org.ckan.Dataset bean=org.ckan.Client.gson.fromJson(new JSONObject().put("name","fixture").put("resources",resources).toString(),org.ckan.Dataset.class);
    assertTrue(bean.getResources().get(0).getAccess_services().isEmpty());
    assertEquals("WMS",bean.getResources().get(1).getAccess_services().get(0).getTitle());
    assertEquals("https://example.org/wms",bean.getResources().get(1).getAccess_services().get(0).getEndpoint_url().get(0));
    assertEquals("API",bean.getResources().get(2).getAccess_services().get(0).getTitle());
    assertTrue(bean.getResources().get(3).getAccess_services().isEmpty());
  }
  @Test void ckanAlternateIdentifierAcceptsStringAndObjectArrays() throws Exception {
    // S034 (dati.toscana.it) mixes ckanext-dcat string arrays with DCAT-AP_IT objects; one string array failed the whole catalogue.
    CkanConnector connector=new CkanConnector(node());
    for(String value:new String[]{"[\"01166557-9be0-4f3f-a88f-7569a8c60c45\"]","[{\"identifier\": \"01166557-9be0-4f3f-a88f-7569a8c60c45\", \"agent\": {}}]"}) {
      JSONObject source=flat().put("name","fixture").put("extras",new JSONArray().put(new JSONObject().put("key","alternate_identifier").put("value",value)));
      org.ckan.Dataset bean=org.ckan.Client.gson.fromJson(source.toString(),org.ckan.Dataset.class);
      DcatDataset dataset=connector.datasetToDcat(bean,node());
      assertTrue(dataset.getOtherIdentifier().stream().anyMatch(p->"01166557-9be0-4f3f-a88f-7569a8c60c45".equals(p.getValue())),value);
    }
  }
  @Test void nativeFederationKeepsConceptUrisAndLanguages() throws Exception {
    JSONObject source=new JSONObject("{\"id\":\"fixture\",\"title\":{\"value\":\"Budget\"},\"description\":{\"value\":\"Annual budget\"},\"theme\":[{\"resourceUri\":\"https://example.org/theme\",\"prefLabel\":[{\"language\":\"en\",\"value\":\"Economy\"}]}],\"datasetDetails\":[{\"language\":\"it\",\"title\":\"Bilancio\",\"description\":\"Dati annuali\"},{\"language\":\"en\",\"title\":\"Budget\",\"description\":\"Annual data\"}],\"previousVersion\":[\"https://example.org/v1\"]}");
    DcatDataset target=new OpenDataFederationNativeConnector(node()).datasetToDcat(source,node());
    assertEquals("https://example.org/theme",target.getTheme().get(0).getResourceUri());
    assertEquals("Economy",target.getTheme().get(0).getPrefLabel().get(0).getValue());
    assertEquals(2,target.getDatasetDetails().size());
    assertEquals("https://example.org/v1",target.getPreviousVersion().get(0));
  }
  @Test void servicesAndPoliciesAcceptCanonicalAndSnakeCaseNames() {
    DcatDistribution distribution=new DcatDistribution();distribution.setNodeId("999999");
    DcatJsonMetadata.apply(distribution,new JSONObject("{\"compressFormat\":\"application/gzip\",\"has_policy\":\"https://example.org/policy\",\"access_services\":[{\"title\":\"API\",\"endpoint_url\":[\"https://example.org/api\"]}]}"));
    assertEquals("application/gzip",distribution.getCompressionFormat().getValue());
    assertEquals("http://www.w3.org/ns/odrl/2/hasPolicy",distribution.getHasPolicy().getProperty().getURI());
    assertEquals("https://example.org/api",distribution.getAccessService().get(0).getEndpointURL().get(0).getValue());
  }
}
