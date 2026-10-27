package it.eng.idra.dcat.dump;

import com.google.gson.*;
import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.connectors.*;
import it.eng.idra.utils.*;
import java.util.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatTypedConnectorTest {
  private OdmsCatalogue node() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("https://example.org");node.setHomepage("https://example.org");return node;
  }
  @Test void openDataSoftPreservesAvailableDcatExtensions() throws Exception {
    String json="{\"dataset\":{\"dataset_id\":\"fixture\",\"fields\":[],\"links\":[],\"metas\":{\"default\":{\"title\":\"Budget\",\"description\":\"Annual data\",\"publisher\":\"Fixture\",\"modified\":\"2026-01-01T00:00:00Z\",\"theme\":[],\"keywords\":[],\"language\":\"en\"},\"dcat\":{\"inSeries\":[{\"resourceUri\":\"https://example.org/series\",\"title\":\"Budgets\"}],\"previousVersion\":[\"https://example.org/v1\"]}}}}";
    it.eng.idra.beans.opendatasoft.Dataset source=new Gson().fromJson(json,it.eng.idra.beans.opendatasoft.Dataset.class);
    source.setLinks(new ArrayList<>());
    DcatDataset dataset=new OpenDataSoftConnector(node()).datasetToDcat(source,node());
    assertEquals("https://example.org/series",dataset.getInSeries().get(0).getResourceUri());
    assertEquals("https://example.org/v1",dataset.getPreviousVersion().get(0));
  }
  @Test void zenodoPreservesDoiAndDoesNotInventHvdOrSeries() throws Exception {
    String json="{\"id\":1,\"doi\":\"10.1234/fixture\",\"created\":\"2026-01-01T00:00:00Z\",\"modified\":\"2026-01-02T00:00:00Z\",\"files\":[],\"links\":{\"html\":\"https://zenodo.org/records/1\"},\"metadata\":{\"title\":\"Budget\",\"description\":\"Annual data\",\"version\":\"2\",\"language\":\"en\",\"access_right\":\"open\",\"resource_type\":{\"type\":\"dataset\"},\"creators\":[],\"keywords\":[]}}";
    it.eng.idra.beans.zenodo.ZenodoDataset.Hit source=new Gson().fromJson(json,it.eng.idra.beans.zenodo.ZenodoDataset.Hit.class);
    DcatDataset dataset=new ZenodoConnector(node()).datasetToDcat(source,node());
    assertEquals("https://doi.org/10.1234/fixture",dataset.getResourceUri());
    assertEquals("2",dataset.getVersion().getValue());
    assertTrue(dataset.getInSeries().isEmpty());
    assertTrue(dataset.getHVDCategory().stream().noneMatch(p->p.getValue()!=null && !p.getValue().isEmpty()));
  }
  @Test void geonetworkPreservesIsoIdentifierAndEdition() throws Exception {
    String xml="<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\"><gmd:fileIdentifier><gco:CharacterString>https://example.org/iso-record</gco:CharacterString></gmd:fileIdentifier><gmd:identificationInfo><gmd:MD_DataIdentification><gmd:citation><gmd:CI_Citation><gmd:title><gco:CharacterString>Budget</gco:CharacterString></gmd:title><gmd:edition><gco:CharacterString>2</gco:CharacterString></gmd:edition></gmd:CI_Citation></gmd:citation><gmd:abstract><gco:CharacterString>Annual data</gco:CharacterString></gmd:abstract></gmd:MD_DataIdentification></gmd:identificationInfo></gmd:MD_Metadata>";
    javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
    org.w3c.dom.Element source=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))).getDocumentElement();
    DcatDataset dataset=new GeoNetworkConnector(node()).datasetToDcat(source,node());
    assertEquals("https://example.org/iso-record",dataset.getResourceUri());
    assertEquals("2",dataset.getVersion().getValue());
  }
  @Test void webSelectorsCanCarryStructuredSeries() throws Exception {
    OdmsCatalogue node=node();
    List<it.eng.idra.beans.webscraper.DatasetSelector> selectors=new ArrayList<>();
    for(String name:Arrays.asList("title","description","inSeries")) selectors.add(new it.eng.idra.beans.webscraper.DatasetSelector(
        Arrays.asList("_root"),it.eng.idra.beans.webscraper.WebScraperSelectorType.SelectorText,false,name,"."+name,null,new ArrayList<>()));
    it.eng.idra.beans.webscraper.WebScraperSitemap sitemap=new it.eng.idra.beans.webscraper.WebScraperSitemap();
    sitemap.setDatasetSelectors(selectors);node.setSitemap(sitemap);
    org.jsoup.nodes.Document source=org.jsoup.Jsoup.parse("<h1 class='title'>Budget</h1><div class='description'>Annual data</div><div class='inSeries'>[{\"resourceUri\":\"https://example.org/series\",\"title\":\"Budgets\"}]</div>","https://example.org/fixture");
    DcatDataset dataset=new WebConnector(node).datasetToDcat(source,node);
    assertEquals("https://example.org/series",dataset.getInSeries().get(0).getResourceUri());
  }
  @Test void spodExtrasUseSameExtensionContractAsCkan() {
    Gson gson=new GsonBuilder().setExclusionStrategies(new ExclusionStrategy(){
      public boolean shouldSkipField(FieldAttributes f){return false;}
      public boolean shouldSkipClass(Class<?> c){return c==java.lang.Package.class;}
    }).create();
    it.eng.idra.beans.spod.SpodDataset source=gson.fromJson("{\"isopen\":true,\"id\":\"fixture\",\"name\":\"fixture\",\"title\":\"Budget\",\"notes\":\"Annual data\",\"resources\":[],\"tags\":[],\"extras\":[{\"key\":\"in_series\",\"value\":\"[\\\"https://example.org/series\\\"]\"}]}",it.eng.idra.beans.spod.SpodDataset.class);
    DcatDataset dataset=new SpodConnector(node()).datasetToDcat(source,node());
    assertEquals("https://example.org/series",dataset.getInSeries().get(0).getResourceUri());
  }
}

