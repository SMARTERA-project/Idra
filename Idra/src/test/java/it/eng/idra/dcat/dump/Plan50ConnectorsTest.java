package it.eng.idra.dcat.dump;

import com.google.gson.Gson;
import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.connectors.CkanConnector;
import it.eng.idra.connectors.GeoNetworkConnector;
import it.eng.idra.connectors.OpenDataSoftConnector;
import it.eng.idra.connectors.SocrataConnector;
import it.eng.idra.connectors.SpodConnector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Plan50: conversions the connector trace found missing, checked on real records frozen by plan62 (src/test/resources/plan50-connectors).
class Plan50ConnectorsTest {
  private static OdmsCatalogue node() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("https://example.org");node.setHomepage("https://example.org");return node;
  }
  private static String resource(String name) throws Exception {
    try(java.io.InputStream in=Plan50ConnectorsTest.class.getResourceAsStream("/plan50-connectors/"+name)) {
      return new String(in.readAllBytes(),StandardCharsets.UTF_8);
    }
  }
  private static DcatDataset ckan(JSONObject record) {
    CkanConnector connector=new CkanConnector(node());
    org.ckan.Dataset source=org.ckan.Client.gson.fromJson(record.toString(),org.ckan.Dataset.class);
    return connector.datasetToDcat(source,node());
  }
  private static JSONObject withExtra(JSONObject record,String key,String value) {
    JSONArray extras=record.getJSONArray("extras");
    for(int i=0;i<extras.length();i++)if(extras.getJSONObject(i).getString("key").equals(key)){extras.getJSONObject(i).put("value",value);return record;}
    extras.put(new JSONObject().put("key",key).put("value",value));return record;
  }

  @Test void ckanSpatialExtrasReachTheLocation() throws Exception {
    // Umbria (S014): GeoJSON "spatial" was read into a variable the location never used; bbox-* and the CRS were ignored.
    DcatDataset dataset=ckan(new JSONObject(resource("ckan-S014.json")));
    DctLocation location=dataset.getSpatialCoverage().get(0);
    assertTrue(location.getGeometry().getValue().contains("\"Polygon\""));
    assertEquals("POLYGON((11.38 42.2289,13.2144 42.2289,13.2144 43.6713,11.38 43.6713,11.38 42.2289))",location.getBbox().getValue());
    assertTrue(dataset.getConformsTo().stream().map(c -> c.getUri()+"|"+(c.getIdentifier()==null?"":c.getIdentifier().getValue()))
        .anyMatch(s -> s.contains("http://www.opengis.net/def/crs/EPSG/0/3004")),"EPSG code 3004 becomes its OGC CRS IRI");
    dataset.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
    RDFNode geometry=out.listObjectsOfProperty(out.createProperty("http://www.w3.org/ns/locn#geometry")).next();
    assertEquals("http://www.opengis.net/ont/geosparql#geoJSONLiteral",geometry.asLiteral().getDatatypeURI());
    RDFNode bbox=out.listObjectsOfProperty(DCAT.bbox).next();
    assertEquals("http://www.opengis.net/ont/geosparql#wktLiteral",bbox.asLiteral().getDatatypeURI());
  }

  @Test void ckanDcatApItCreatorAndTemporalCoverage() throws Exception {
    // Genova (S008): creator with every language of its name and its IPA identifier.
    DcatDataset genova=ckan(new JSONObject(resource("ckan-S008.json")));
    assertEquals("c_d969",genova.getCreator().getIdentifier().getValue());
    Map<String,String> names=new HashMap<>();
    for(DcatProperty name:genova.getCreator().getName())names.put(name.getLanguage(),name.getValue());
    assertEquals(Set.of("it","en","de","fr"),names.keySet());
    assertEquals("Comune di Genova - servizi civici",names.get("it"));
    // temporal_coverage as published by DCAT-AP_IT portals (value observed in the frozen sources).
    DcatDataset period=ckan(withExtra(new JSONObject(resource("ckan-S008.json")),"temporal_coverage",
        "[{\"temporal_end\": \"2013-12-31\", \"temporal_start\": \"2013-01-01\"}]"));
    assertEquals(1,period.getTemporalCoverage().size());
    assertEquals("2013-01-01",period.getTemporalCoverage().get(0).getStartDate().getValue().substring(0,10));
    assertEquals("2013-12-31",period.getTemporalCoverage().get(0).getEndDate().getValue().substring(0,10));
    // Plain temporal_start / temporal_end extras (Prato, MIT and others).
    JSONObject plain=withExtra(withExtra(new JSONObject(resource("ckan-S075.json")),"temporal_start","2016-01-01"),"temporal_end","2016-12-31");
    DcatDataset prato=ckan(plain);
    assertEquals("2016-01-01",prato.getTemporalCoverage().get(0).getStartDate().getValue().substring(0,10));
  }

  @Test void ckanThemesAggregateAndResponsibleParty() throws Exception {
    // Without the "theme" extra, themes_aggregate still yields the EU theme; responsible-party is the contact fallback.
    JSONObject record=new JSONObject(resource("ckan-S014.json"));
    JSONArray extras=record.getJSONArray("extras"),kept=new JSONArray();
    for(int i=0;i<extras.length();i++)if(!extras.getJSONObject(i).getString("key").equals("theme"))kept.put(extras.getJSONObject(i));
    record.put("extras",kept).put("maintainer","").put("author","");
    DcatDataset dataset=ckan(record);
    assertTrue(dataset.getTheme().stream().anyMatch(t -> String.valueOf(t.getResourceUri()).endsWith("/ENVI")
        || t.getPrefLabel().stream().anyMatch(l -> "Environment".equals(l.getValue()))),"themes_aggregate ENVI");
    assertEquals("ARPA Umbria",dataset.getContactPoint().get(0).getFn().getValue());
  }

  @Test void openDataSoftAttachmentsAreDocumentation() throws Exception {
    // Paris (S086): the bean field was misspelled "attachements", so the API "attachments" list was always empty.
    it.eng.idra.beans.opendatasoft.Dataset source=new Gson().fromJson(resource("ods-S086.json"),it.eng.idra.beans.opendatasoft.Dataset.class);
    if(source.getLinks()==null)source.setLinks(new ArrayList<>());
    DcatDataset dataset=new OpenDataSoftConnector(node()).datasetToDcat(source,node());
    assertTrue(dataset.getDocumentation().stream().map(DcatProperty::getValue).collect(Collectors.toList())
        .contains("https://opendata.paris.fr/api/v2/catalog/datasets/plu-annexes-biens-inscrits-au-patrimoine-mondial/attachments/anx_pat_unesco_pdf"));
  }

  @Test void geoNetworkRecordKeepsPeriodContactFrequencyAndLineage() throws Exception {
    // Torino (S091): GML 3.2 periods, the record's own contact, ISO frequency and lineage were all dropped.
    javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
    org.w3c.dom.Element source=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(resource("iso-S091.xml").getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    DcatDataset dataset=new GeoNetworkConnector(node()).datasetToDcat(source,node());
    assertEquals("2010-03-01",dataset.getTemporalCoverage().get(0).getStartDate().getValue().substring(0,10));
    VcardOrganization contact=dataset.getContactPoint().get(0);
    assertFalse(contact.getHasEmail().getValue().isBlank(),"email of the record point of contact");
    assertEquals("NOT_PLANNED",dataset.getFrequency().getValue());
    assertTrue(dataset.getProvenance().stream().anyMatch(p -> p.getValue().startsWith("Estrazione dati IPLA")));
    dataset.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
    assertTrue(out.contains(null,DCTerms.accrualPeriodicity,out.getResource(DcatApSerializer.FREQUENCY_BASE_URI+"NOT_PLANNED")));
  }

  @Test void socrataAccessLevelAndDescribedBy() throws Exception {
    // Friuli Venezia Giulia (S001): DCAT-US accessLevel and the distribution data dictionary (describedBy) were dropped.
    DcatDataset dataset=new SocrataConnector(node()).datasetToDcat(new JSONObject(resource("socrata-S001.json")),node());
    assertEquals("http://publications.europa.eu/resource/authority/access-right/PUBLIC",dataset.getAccessRights().getValue());
    List<String> schemas=dataset.getDistributions().stream().filter(d -> d.getLinkedSchemas()!=null)
        .flatMap(d -> d.getLinkedSchemas().stream()).map(DctStandard::getUri).collect(Collectors.toList());
    assertEquals(List.of("https://www.dati.friuliveneziagiulia.it/api/views/22k2-rbwv/columns.json",
        "https://www.dati.friuliveneziagiulia.it/api/views/22k2-rbwv/columns.xml"),schemas);
    assertNull(SocrataConnector.accessRight("someLocalLevel"),"no invented mapping for unknown access levels");
    dataset.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
    assertTrue(out.contains(null,DCTerms.accessRights,out.getResource("http://publications.europa.eu/resource/authority/access-right/PUBLIC")));
    assertTrue(out.contains(null,DCTerms.conformsTo,out.getResource("https://www.dati.friuliveneziagiulia.it/api/views/22k2-rbwv/columns.json")));
  }

  @Test void spodLicenceKeepsItsTitle() throws Exception {
    // Pisa (S067): the licence name was its id ("cc-by"), the title "Creative Commons Attribution" was lost.
    String body=SpodConnector.normalizeJsonArrayField(resource("spod-S067.json"),"language");
    it.eng.idra.beans.spod.SpodDataset source=it.eng.idra.utils.GsonUtil.json2Obj(body,it.eng.idra.utils.GsonUtil.spodDatasetType);
    DcatDataset dataset=new SpodConnector(node()).datasetToDcat(source,node());
    DctLicenseDocument licence=dataset.getDistributions().get(0).getLicense();
    assertEquals("Creative Commons Attribution",licence.getName().getValue());
    assertEquals("http://www.opendefinition.org/licenses/cc-by",licence.getUri());
  }

  @Test void licenceCodeIsNotALicenceType() throws Exception {
    // SPOD (Pisa S067) and CKAN (Umbria S014) stored the portal licence id ("cc-by") as licence type,
    // exported as a concept that does not exist in the ADMS licence type vocabulary.
    String body=SpodConnector.normalizeJsonArrayField(resource("spod-S067.json"),"language");
    it.eng.idra.beans.spod.SpodDataset spod=it.eng.idra.utils.GsonUtil.json2Obj(body,it.eng.idra.utils.GsonUtil.spodDatasetType);
    DcatDataset pisa=new SpodConnector(node()).datasetToDcat(spod,node());
    DcatDataset umbria=ckan(new JSONObject(resource("ckan-S014.json")));
    Property type=DCTerms.type;
    for(DcatDataset dataset:List.of(pisa,umbria)) {
      assertTrue(dataset.getDistributions().stream().allMatch(d -> d.getLicense().getType().getValue().isBlank()));
      dataset.setMetadataEnvelope(null);
      Model out=DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
      assertTrue(out.listResourcesWithProperty(RDF.type,DCTerms.LicenseDocument).hasNext(),"licence still exported");
      assertFalse(out.listStatements(null,type,(RDFNode)null).toList().stream()
          .anyMatch(s -> s.getObject().toString().startsWith(DcatApSerializer.LICENSE_TYPE_BASE_URI)),"no licence type from a code");
    }
    // Values already stored (or set by other connectors): only IRIs and ADMS codes are licence types.
    assertNull(DcatApSerializer.licenceTypeUri("cc-by"));
    assertNull(DcatApSerializer.licenceTypeUri("License IODL v2.0"));
    assertEquals(DcatApSerializer.LICENSE_TYPE_BASE_URI+"Attribution",DcatApSerializer.licenceTypeUri("Attribution"));
    assertEquals(DcatApSerializer.LICENSE_TYPE_BASE_URI+"PublicDomain",DcatApSerializer.licenceTypeUri(DcatApSerializer.LICENSE_TYPE_BASE_URI+"PublicDomain"));
  }

  @Test void ckanResourceLicenceTypeIsTheLicenceIri() throws Exception {
    // Genova (S008): DCAT-AP_IT resources give their licence IRI in license_type (Italian licences vocabulary),
    // here different from the dataset licence (cc-by-sa): the distribution keeps the resource licence, not the dataset name.
    String ccby40="https://w3id.org/italia/controlled-vocabulary/licences/A21_CCBY40";
    JSONObject record=new JSONObject(resource("ckan-S008.json"));
    DcatDataset genova=ckan(record);
    JSONArray resources=record.getJSONArray("resources");
    assertEquals(resources.length(),genova.getDistributions().size());
    for(int i=0;i<resources.length();i++) {
      DctLicenseDocument licence=genova.getDistributions().get(i).getLicense();
      assertEquals(resources.getJSONObject(i).getString("license_type"),licence.getUri(),"resource "+i);
      assertTrue(licence.getName().getValue().isBlank(),"no dataset licence name on the resource licence");
    }
    genova.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(genova,ModelFactory.createDefaultModel());
    assertTrue(out.contains(null,DCTerms.license,out.getResource(ccby40)));
    assertTrue(out.contains(out.getResource(ccby40),RDF.type,DCTerms.LicenseDocument));
    // The resource licence name (license_id) goes with its IRI; a plain code keeps the dataset licence.
    DctLicenseDocument dataset=new DctLicenseDocument("http://www.opendefinition.org/licenses/cc-by","cc-by","","","1");
    DctLicenseDocument named=CkanConnector.resourceLicense(new JSONObject().put("license_type",ccby40)
        .put("license_id","Creative Commons Attribuzione 4.0 Internazionale (CC BY 4.0)"),dataset,"1");
    assertEquals("Creative Commons Attribuzione 4.0 Internazionale (CC BY 4.0)",named.getName().getValue());
    assertSame(dataset,CkanConnector.resourceLicense(new JSONObject().put("license_type","cc-by"),dataset,"1"));
    assertSame(dataset,CkanConnector.resourceLicense(new JSONObject(),dataset,"1"));
    // Firenze Metropolitana (S075): resources without license_type keep the dataset licence.
    DcatDataset firenze=ckan(new JSONObject(resource("ckan-S075.json")));
    assertTrue(firenze.getDistributions().stream().allMatch(d -> "http://www.opendefinition.org/licenses/cc-by".equals(d.getLicense().getUri())));
  }

  @Test void localResourceIdIsNotAnIri() throws Exception {
    // Roma (S003): a resource has "uri": "distr340"; as distribution IRI it was relative and broke the whole catalogue export.
    DcatDataset roma=ckan(new JSONObject(resource("ckan-S003.json")));
    assertEquals(41,roma.getDistributions().size());
    assertTrue(roma.getDistributions().stream().noneMatch(d -> "distr340".equals(d.getResourceUri())));
    roma.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(roma,ModelFactory.createDefaultModel());
    out.listSubjects().forEachRemaining(s -> assertTrue(s.isAnon() || s.getURI().matches("(?i)(https?|urn):.+"),s.toString()));
    java.io.ByteArrayOutputStream rdf=new java.io.ByteArrayOutputStream();
    org.apache.jena.riot.RDFDataMgr.write(rdf,out,org.apache.jena.riot.RDFFormat.RDFXML);
    assertTrue(rdf.size()>0);
  }

  @Test void apiLicenceWithoutIriIsEmpty() throws Exception {
    // The dct:license property IRI was the uri of every licence without IRI: API, broker and portal showed it as a licence.
    DcatDistribution empty=new DcatDistribution("999999");empty.setLicense(null);
    assertEquals("",empty.getLicense().getUri());
    assertEquals("",new DctLicenseDocument(null,"cc-by","","","1").getUri());
    DctLicenseDocument stored=new DctLicenseDocument();stored.setUri(DCTerms.license.getURI());
    assertEquals("",stored.getUri(),"placeholder already stored is cleared when read");
    assertEquals("https://example.org/l",new DctLicenseDocument("https://example.org/l","","","","1").getUri());
    // Umbria (S014) dataset without licence id, title or URL: its distributions expose no licence IRI.
    JSONObject record=new JSONObject(resource("ckan-S014.json")).put("license_id","").put("license_title","").put("license_url","");
    for(int i=0;i<record.getJSONArray("resources").length();i++)record.getJSONArray("resources").getJSONObject(i).remove("license_type");
    DcatDataset dataset=ckan(record);
    String json=it.eng.idra.utils.GsonUtil.obj2Json(dataset.getDistributions(),it.eng.idra.utils.GsonUtil.distributionListType);
    assertFalse(json.contains(DCTerms.license.getURI()),json.length()>300?json.substring(0,300):json);
  }

  @Test void distributionWithoutLicenceHasNoPlaceholderLicence() {
    // An empty licence is stored with the dct:license property IRI as placeholder: it must not become a licence.
    DcatDistribution distribution=new DcatDistribution("999999");distribution.setLicense(null);
    distribution.setAccessUrl("https://example.org/file.csv");
    Model out=ModelFactory.createDefaultModel();Resource d=out.createResource("https://example.org/dist");
    DcatApSerializer.serializeLicense(distribution.getLicense(),out,d);
    assertFalse(out.contains(d,DCTerms.license),"no licence for a distribution without licence");
    Model named=ModelFactory.createDefaultModel();Resource n=named.createResource("https://example.org/dist");
    DcatApSerializer.serializeLicense(new DctLicenseDocument(DCTerms.license.getURI(),"cc-by-4.0","","","999999"),named,n);
    assertTrue(named.getProperty(n,DCTerms.license).getObject().isAnon(),"a named licence without IRI is a blank node");
  }

  @Test void connectorStandardsExportTheirOwnIri() throws Exception {
    // Connectors store the dct:conformsTo property IRI as "uri" and the standard IRI as "identifier":
    // the export must point to the standard (here the CKAN EPSG reference system), not to dct:conformsTo.
    DcatDataset dataset=ckan(new JSONObject(resource("ckan-S014.json")));
    dataset.setMetadataEnvelope(null);
    Model out=DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
    assertTrue(out.contains(null,DCTerms.conformsTo,out.getResource("http://www.opengis.net/def/crs/EPSG/0/3004")));
    assertFalse(out.contains(null,DCTerms.conformsTo,out.getResource(DCTerms.conformsTo.getURI())));
    assertFalse(out.listStatements(null,DCTerms.title,"").hasNext(),"no empty standard titles");
  }

  @Test void euFrequencyCodesAreNotTurnedIntoUnknown() {
    for(String value:List.of("WEEKLY","AS_NEEDED","10MIN",DcatApSerializer.FREQUENCY_BASE_URI+"MONTHLY")) {
      Model out=ModelFactory.createDefaultModel();Resource d=out.createResource("https://example.org/d");
      DcatApSerializer.serializeFrequency(new DcatProperty(DCTerms.accrualPeriodicity,DCTerms.Frequency,value),out,d);
      String code=value.substring(value.lastIndexOf('/')+1);
      assertTrue(out.contains(d,DCTerms.accrualPeriodicity,out.getResource(DcatApSerializer.FREQUENCY_BASE_URI+code)),value);
    }
    assertNull(DctFrequency.fromIso19115("someLocalCode"),"no invented mapping for unknown ISO codes");
  }
}
