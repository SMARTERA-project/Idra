package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.connectors.CkanConnector;
import it.eng.idra.connectors.SpodConnector;
import it.eng.idra.utils.GeoNetworkConnectorUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Plan50 step 5: values Idra used to invent when a source had none, and the DCAT-AP 3.0.1 full-validation findings
// caused by Idra's export (byteSize datatype, classes of language/frequency/landing page/sub-catalogue, file types).
class Plan50InventedValuesTest {
  private static OdmsCatalogue node() {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("https://example.org");node.setHomepage("https://example.org");return node;
  }
  private static String resource(String name) throws Exception {
    try(java.io.InputStream in=Plan50InventedValuesTest.class.getResourceAsStream("/plan50-connectors/"+name)) {
      return new String(in.readAllBytes(),StandardCharsets.UTF_8);
    }
  }
  private static DcatDataset ckan(String name) throws Exception {
    // The connector configures the CKAN client's JSON reader: create it before parsing.
    CkanConnector connector=new CkanConnector(node());
    org.ckan.Dataset source=org.ckan.Client.gson.fromJson(resource(name),org.ckan.Dataset.class);
    return connector.datasetToDcat(source,node());
  }
  private static Model export(DcatDataset dataset) {
    dataset.setMetadataEnvelope(null);
    return DcatApSerializer.addDatasetToModel(dataset,ModelFactory.createDefaultModel());
  }

  @Test void noInventedDates() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",
        source.createResource("https://example.org/dataset/0",DCAT.Dataset).addProperty(DCTerms.title,"No dates"));
    assertEquals("",dataset.getReleaseDate().getValue());
    assertEquals("",dataset.getUpdateDate().getValue());
    Model out=export(dataset);
    assertFalse(out.contains(null,DCTerms.issued,(RDFNode)null));
    assertFalse(out.contains(null,DCTerms.modified,(RDFNode)null));
    // A value saved by an older version is cleaned when it is read back.
    DcatDistribution distribution=new DcatDistribution();
    distribution.setReleaseDate(new DcatProperty(DCTerms.issued,RDFS.Literal,DcatProperty.INVENTED_DATE));
    distribution.setUpdateDate(DcatProperty.INVENTED_DATE);
    assertEquals("",distribution.getReleaseDate().getValue());
    assertEquals("",distribution.getUpdateDate().getValue());
    distribution.setUpdateDate("2026-10-27T10:00:00Z");
    assertEquals("2026-10-27T10:00:00Z",distribution.getUpdateDate().getValue());
  }

  @Test void noInventedLandingPageAndRealOneIsADocument() throws Exception {
    Model source=ModelFactory.createDefaultModel();
    Resource without=source.createResource("https://example.org/dataset/1",DCAT.Dataset).addProperty(DCTerms.title,"A");
    Resource literal=source.createResource("https://example.org/dataset/2",DCAT.Dataset).addProperty(DCTerms.title,"B")
        .addProperty(DCAT.landingPage,"https://example.org/page/2");
    DcatApDeserializer parser=new DcatApDeserializer();
    DcatDataset a=parser.resourceToDataset("999999",without);
    assertEquals("",a.getLandingPage().getValue(),"the dataset IRI is not its landing page");
    assertFalse(export(a).contains(null,DCAT.landingPage,(RDFNode)null));
    DcatDataset b=parser.resourceToDataset("999999",literal);
    assertEquals("https://example.org/page/2",b.getLandingPage().getValue(),"a literal landing page is source data");
    Model out=export(b);
    Resource page=out.getResource("https://example.org/page/2");
    assertTrue(out.contains(null,DCAT.landingPage,page));
    assertTrue(out.contains(page,RDF.type,FOAF.Document));
  }

  @Test void noInventedFrequency() {
    Model out=ModelFactory.createDefaultModel();Resource d=out.createResource("https://example.org/d");
    DcatApSerializer.serializeFrequency(new DcatProperty(DCTerms.accrualPeriodicity,DCTerms.Frequency,""),out,d);
    DcatApSerializer.serializeFrequency(null,out,d);
    assertFalse(out.contains(d,DCTerms.accrualPeriodicity,(RDFNode)null),"no UNKNOWN when the source has no frequency");
    DcatApSerializer.serializeFrequency(new DcatProperty(DCTerms.accrualPeriodicity,DCTerms.Frequency,"irreg"),out,d);
    Resource irregular=out.getResource(DcatApSerializer.FREQUENCY_BASE_URI+"IRREG");
    assertTrue(out.contains(d,DCTerms.accrualPeriodicity,irregular));
    assertTrue(out.contains(irregular,RDF.type,DCTerms.Frequency));
    Model text=ModelFactory.createDefaultModel();Resource t=text.createResource("https://example.org/t");
    DcatApSerializer.serializeFrequency(new DcatProperty(DCTerms.accrualPeriodicity,DCTerms.Frequency,"every full moon"),text,t);
    Resource label=text.listObjectsOfProperty(t,DCTerms.accrualPeriodicity).next().asResource();
    assertTrue(label.isAnon());
    assertEquals("every full moon",label.getProperty(RDFS.label).getString());
  }

  @Test void noInventedLicenceName() throws Exception {
    JSONObject record=new JSONObject(resource("ckan-S008.json"));
    record.remove("license_id");record.remove("license_title");
    CkanConnector connector=new CkanConnector(node());
    org.ckan.Dataset source=org.ckan.Client.gson.fromJson(record.toString(),org.ckan.Dataset.class);
    DcatDataset dataset=connector.datasetToDcat(source,node());
    for(DcatDistribution d:dataset.getDistributions())
      assertNotEquals("unknown",d.getLicense().getName().getValue());
  }

  @Test void absentSizeIsNoSizeAndByteSizeIsAnInteger() throws Exception {
    // Genova (S008): every resource has "size": null; CKANClient's int made them 0 bytes.
    DcatDataset genova=ckan("ckan-S008.json");
    for(DcatDistribution d:genova.getDistributions())
      assertTrue(d.getByteSize()==null||d.getByteSize().getValue()==null||d.getByteSize().getValue().isEmpty(),"no invented 0");
    assertNull(CkanConnector.resourceSize(new JSONObject().put("size",JSONObject.NULL)));
    assertEquals("519",CkanConnector.resourceSize(new JSONObject().put("size","519")));
    // Pisa (S067): SPOD resources have no size at all.
    String body=SpodConnector.normalizeJsonArrayField(resource("spod-S067.json"),"language");
    DcatDataset pisa=new SpodConnector(node()).datasetToDcat(SpodConnector.parseDataset(body),node());
    for(DcatDistribution d:pisa.getDistributions())
      assertTrue(d.getByteSize()==null||d.getByteSize().getValue()==null||d.getByteSize().getValue().isEmpty(),"no invented 0");

    Model out=ModelFactory.createDefaultModel();Resource r=out.createResource("https://example.org/r");
    DcatApSerializer.serializeByteSize(new DcatProperty(DCAT.byteSize,RDFS.Literal,"519"),out,r);
    DcatApSerializer.serializeByteSize(new DcatProperty(DCAT.byteSize,RDFS.Literal,"12.5"),out,r);
    DcatApSerializer.serializeByteSize(new DcatProperty(DCAT.byteSize,RDFS.Literal,"-1"),out,r);
    List<RDFNode> sizes=out.listObjectsOfProperty(r,DCAT.byteSize).toList();
    assertEquals(1,sizes.size());
    assertEquals(XSDDatatype.XSDnonNegativeInteger.getURI(),sizes.get(0).asLiteral().getDatatypeURI());
    assertEquals("519",sizes.get(0).asLiteral().getLexicalForm());
  }

  @Test void formatsAreEuFileTypesOrLabels() {
    String base=DcatApSerializer.FORMAT_BASE_URI;
    assertEquals(base+"GEOJSON",DcatApSerializer.euFileType("GeoJSON"));
    assertEquals(base+"KMZ",DcatApSerializer.euFileType(".kmz"));
    assertEquals(base+"CSV",DcatApSerializer.euFileType("csv"));
    assertEquals(base+"RDF_TURTLE",DcatApSerializer.euFileType("TURTLE"));
    assertEquals(base+"WMS_SRVC",DcatApSerializer.euFileType("WMS"));
    assertEquals(base+"GEOJSON",DcatApSerializer.euFileType(base+"GeoJSON"));
    assertNull(DcatApSerializer.euFileType("tif - tfw"));
    Model out=ModelFactory.createDefaultModel();Resource r=out.createResource("https://example.org/r");
    DcatApSerializer.serializeFormat(new DcatProperty(DCTerms.format,DCTerms.MediaTypeOrExtent,"tif - tfw"),out,r);
    Resource label=out.listObjectsOfProperty(r,DCTerms.format).next().asResource();
    assertTrue(label.isAnon(),"no file-type IRI that does not exist");
    assertEquals("tif - tfw",label.getProperty(RDFS.label).getString());
    assertTrue(label.hasProperty(RDF.type,DCTerms.MediaTypeOrExtent));
    assertFalse(out.listSubjects().toList().stream().anyMatch(s->s.isURIResource()&&s.getURI().startsWith(base)));
  }

  @Test void openDataSoftFrequenciesAndMediaTypes() {
    // Paris (S086): frequencies in French, format codes as media types.
    assertEquals("ANNUAL",DctFrequency.fromLabel("Annuelle"));
    assertEquals("NEVER",DctFrequency.fromLabel("Pas de mise à jour"));
    assertNull(DctFrequency.fromLabel("Ponctuelle"),"no equivalent: the text is kept");
    Model out=ModelFactory.createDefaultModel();Resource d=out.createResource("https://example.org/d");
    DcatApSerializer.serializeFrequency(new DcatProperty(DCTerms.accrualPeriodicity,DCTerms.Frequency,"Mensuelle"),out,d);
    assertTrue(d.hasProperty(DCTerms.accrualPeriodicity,out.getResource(DcatApSerializer.FREQUENCY_BASE_URI+"MONTHLY")));
    assertEquals(DcatApSerializer.MEDIATYPE_BASE_URI+"application/rdf+xml",DcatApSerializer.ianaMediaType("rdfxml"));
    assertEquals(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv",DcatApSerializer.ianaMediaType("text/csv"));
    assertNull(DcatApSerializer.ianaMediaType("something"));
    assertEquals("http://www.iana.org/assignments/media-types/text/csv",
        DcatApSerializer.ianaMediaType("http://www.iana.org/assignments/media-types/text/csv"),"a source IRI is not rewritten");
    Resource r=out.createResource("https://example.org/r");
    DcatApSerializer.serializeMediaType(new DcatProperty(DCAT.mediaType,DCTerms.MediaType,"something"),out,r);
    Resource label=out.listObjectsOfProperty(r,DCAT.mediaType).next().asResource();
    assertTrue(label.isAnon(),"never a media-types/<text> IRI that IANA does not register");
  }

  @Test void languagesAreLinguisticSystems() {
    Model out=ModelFactory.createDefaultModel();Resource d=out.createResource("https://example.org/d");
    DcatApSerializer.serializeLanguage(List.of(new DcatProperty(DCTerms.language,DCTerms.LinguisticSystem,"it"),
        new DcatProperty(DCTerms.language,DCTerms.LinguisticSystem,DcatApSerializer.LANGUAGE_BASE_URI+"ENG")),out,d);
    for(String code:List.of("ITA","ENG")) {
      Resource language=out.getResource(DcatApSerializer.LANGUAGE_BASE_URI+code);
      assertTrue(out.contains(d,DCTerms.language,language),code);
      assertTrue(out.contains(language,RDF.type,DCTerms.LinguisticSystem),code);
    }
  }

  @Test void noInventedGeoNetworkAccessRights() throws Exception {
    org.w3c.dom.Element record=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new org.xml.sax.InputSource(new java.io.StringReader("<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"/>")))
        .getDocumentElement();
    assertEquals("",GeoNetworkConnectorUtils.extractAccessRightsShort(record),"no restriction found is not 'other restrictions'");
  }

  @Test void exportGivesUntypedObjectsTheirRangeClass() {
    // Source triples restored from the envelope (Eurostat, Palermo) omit the classes DCAT-AP gives by range.
    Model out=ModelFactory.createDefaultModel();
    Resource dataset=out.createResource("https://example.org/d",DCAT.Dataset);
    Resource italian=out.createResource(DcatApSerializer.LANGUAGE_BASE_URI+"ITA");
    Resource csv=out.createResource(DcatApSerializer.FORMAT_BASE_URI+"CSV");
    Resource theme=out.createResource("http://eurovoc.europa.eu/100168");
    dataset.addProperty(DCTerms.language,italian).addProperty(DCTerms.format,csv).addProperty(DCAT.theme,theme)
        .addProperty(DCAT.landingPage,dataset).addProperty(DCTerms.publisher,out.createResource("https://example.org/agent"));
    DcatApSerializer.addRangeClasses(out);
    assertTrue(out.contains(italian,RDF.type,DCTerms.LinguisticSystem));
    assertTrue(out.contains(csv,RDF.type,DCTerms.MediaTypeOrExtent));
    assertFalse(out.contains(theme,RDF.type,(RDFNode)null),"a concept would need a label the source does not give");
    assertFalse(out.contains(dataset,RDF.type,FOAF.Document),"a typed resource keeps its own class");
    assertFalse(out.contains(out.getResource("https://example.org/agent"),RDF.type,(RDFNode)null));
    // Genova (CKAN): alternative identifiers were blank nodes without adms:Identifier.
    Resource identifier=out.createResource().addProperty(SKOS.notation,"local-id");
    dataset.addProperty(out.createProperty("http://www.w3.org/ns/adms#identifier"),identifier);
    DcatApSerializer.addRangeClasses(out);
    assertTrue(identifier.hasProperty(RDF.type,out.createResource("http://www.w3.org/ns/adms#Identifier")));
    // OpenDataSoft and GeoNetwork sources write formats, media types and rights as text.
    Resource ods=out.createResource("https://example.org/ods");
    ods.addProperty(DCTerms.format,"geojson").addProperty(DCAT.mediaType,"text/csv").addProperty(DCAT.mediaType,"xls")
        .addProperty(DCTerms.accessRights,"otherRestrictions");
    DcatApSerializer.addRangeClasses(out);
    assertTrue(ods.hasProperty(DCTerms.format,out.getResource(DcatApSerializer.FORMAT_BASE_URI+"GEOJSON")));
    assertTrue(ods.hasProperty(DCAT.mediaType,out.getResource(DcatApSerializer.MEDIATYPE_BASE_URI+"text/csv")));
    for(Property p:List.of(DCAT.mediaType,DCTerms.accessRights,DCTerms.format))
      assertTrue(out.listObjectsOfProperty(ods,p).toList().stream().noneMatch(RDFNode::isLiteral),p.toString());
    Resource rights=out.listObjectsOfProperty(ods,DCTerms.accessRights).next().asResource();
    assertEquals("otherRestrictions",rights.getProperty(RDFS.label).getString());
    assertTrue(rights.hasProperty(RDF.type,DCTerms.RightsStatement));
    // Eurostat writes byte sizes as text: same number, DCAT-AP datatype, no duplicate.
    Resource distribution=out.createResource("https://example.org/r");
    distribution.addProperty(DCAT.byteSize,"7753606").addProperty(DCAT.byteSize,out.createTypedLiteral("7753606",XSDDatatype.XSDnonNegativeInteger));
    DcatApSerializer.addRangeClasses(out);
    List<RDFNode> sizes=out.listObjectsOfProperty(distribution,DCAT.byteSize).toList();
    assertEquals(1,sizes.size());
    assertEquals(XSDDatatype.XSDnonNegativeInteger.getURI(),sizes.get(0).asLiteral().getDatatypeURI());
  }

  @Test void subCatalogueKeepsItsClassAndDescription() {
    Model source=ModelFactory.createDefaultModel();
    Resource child=source.createResource("https://example.org/catalog/child",DCAT.Catalog)
        .addProperty(DCTerms.title,"Child").addProperty(DCAT.dataset,source.createResource("https://example.org/d/9"));
    Resource root=source.createResource("https://example.org/catalog",DCAT.Catalog).addProperty(DCTerms.title,"Root")
        .addProperty(DCTerms.hasPart,child);
    OdmsCatalogue node=node();node.setSourceMetadata(DcatCatalogueSource.capture(root));
    Model out=ModelFactory.createDefaultModel();Resource target=out.createResource("urn:idra:catalogue:1",DCAT.Catalog);
    DcatCatalogueSource.write(node,target);
    Resource copied=out.getResource(child.getURI());
    assertTrue(out.contains(target,DCTerms.hasPart,copied));
    assertTrue(out.contains(copied,RDF.type,DCAT.Catalog));
    assertTrue(out.contains(copied,DCTerms.title,"Child"));
    assertFalse(out.contains(copied,DCAT.dataset,(RDFNode)null),"members of the sub-catalogue are not copied");
  }
}
