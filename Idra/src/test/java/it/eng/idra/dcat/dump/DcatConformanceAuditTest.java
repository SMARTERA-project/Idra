package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.utils.NgsiDcatReader;
import it.eng.idra.utils.DcatJsonMetadata;
import it.eng.idra.beans.odms.OdmsCatalogue;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.shacl.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline validator. Audit findings are data, not assertions of source conformance. */
class DcatConformanceAuditTest {
  private static final String SH="http://www.w3.org/ns/shacl#";
  private static Model read(Path path) { return RDFDataMgr.loadModel(path.toUri().toString()); }
  private static Model turtle(String text) {
    Model model=ModelFactory.createDefaultModel();
    RDFParser.fromString(text).lang(Lang.TURTLE).parse(model); return model;
  }
  private static Model shapes(String... files) throws Exception {
    Model model=ModelFactory.createDefaultModel();
    for(String name:files) RDFDataMgr.read(model,DcatConformanceAuditTest.class.getResourceAsStream("/dcat3/shacl/"+name),Lang.TURTLE);
    return model;
  }
  @Test void detectsCoreRangeCardinalityAndDatatypeViolations() throws Exception {
    Model rules=shapes("shapes.ttl","range.ttl");
    String prefixes="@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. @prefix xsd:<http://www.w3.org/2001/XMLSchema#>. ";
    Model good=turtle(prefixes+"<urn:test:dataset> a dcat:Dataset; dct:title \"Title\"@en; dct:description \"Description\"@en.");
    assertTrue(ShaclValidator.get().validate(rules.getGraph(),good.getGraph()).conforms());
    for(String statements:Arrays.asList(
        "dct:description \"Description\"", // missing title
        "dct:title <urn:test:not-a-literal>; dct:description \"Description\"",
        "dct:title \"Title\"; dct:description \"Description\"; dct:issued \"2026-01-01\"^^xsd:date, \"2026-01-02\"^^xsd:date",
        "dct:title \"Title\"; dct:description \"Description\"; dcat:distribution <urn:test:untyped>")) {
      assertFalse(ShaclValidator.get().validate(rules.getGraph(),turtle(prefixes+"<urn:test:dataset> a dcat:Dataset; "+statements+".").getGraph()).conforms(),statements);
    }
  }
  @Test void detectsAuthorityViolationWithoutInventingMembership() throws Exception {
    Model rules=shapes("mdr-vocabularies.shape.ttl");
    Model wrong=turtle("@prefix dcat:<http://www.w3.org/ns/dcat#>. <urn:test:distribution> a dcat:Distribution; dcat:mediaType <https://example.org/fictional-media-type>.");
    assertFalse(ShaclValidator.get().validate(rules.getGraph(),wrong.getGraph()).conforms());
  }
  @Test void hvdChecksApplyOnlyToAnExplicitHvdFixture() throws Exception {
    Model rules=shapes("hvd-full.ttl"),data=ModelFactory.createDefaultModel();
    RDFDataMgr.read(data,getClass().getResourceAsStream("/dcat3/hvd.ttl"),Lang.TURTLE);
    ValidationReport positive=ShaclValidator.get().validate(rules.getGraph(),data.getGraph());
    assertTrue(positive.conforms(),positive.getModel().toString());
    data.removeAll(data.getResource("https://example.org/dataset"),data.createProperty("http://data.europa.eu/r5r/applicableLegislation"),null);
    assertFalse(ShaclValidator.get().validate(rules.getGraph(),data.getGraph()).conforms());
  }
  @Test void hvd3DetectsMissingRequiredLegislation() throws Exception {
    Model rules=shapes("hvd3-full.ttl"),data=ModelFactory.createDefaultModel();
    RDFDataMgr.read(data,getClass().getResourceAsStream("/dcat3/hvd.ttl"),Lang.TURTLE);
    RDFDataMgr.read(data,getClass().getResourceAsStream("/dcat3/hvd3-api.ttl"),Lang.TURTLE);
    ValidationReport positive=ShaclValidator.get().validate(rules.getGraph(),data.getGraph());
    long violations=positive.getModel().listStatements(null,positive.getModel().createProperty(SH+"resultSeverity"),positive.getModel().createResource(SH+"Violation")).toList().size();
    assertEquals(0,violations,positive.getModel().toString());
    data.removeAll(data.getResource("https://example.org/dataset"),data.createProperty("http://data.europa.eu/r5r/applicableLegislation"),null);
    ValidationReport negative=ShaclValidator.get().validate(rules.getGraph(),data.getGraph());
    assertTrue(negative.getModel().contains(null,negative.getModel().createProperty(SH+"resultSeverity"),negative.getModel().createResource(SH+"Violation")));
  }
  @Test void auditSnapshotsWhenExplicitlyRequested() throws Exception {
    String rootValue=System.getProperty("conformance.root");
    // Ordinary builds execute validator self-tests; snapshots are an explicit opt-in.
    if(rootValue==null) return;
    Path root=Path.of(rootValue),bundle=root.resolve("bundle"),out=root.resolve(System.getProperty("conformance.output","reports")); Files.createDirectories(out);
    Path snapshotRoot=Path.of(System.getProperty("conformance.snapshotRoot",rootValue));
    for(String manifest:Arrays.asList("sources.json","imports-manifest.json","extra-manifest.json")) {
      JSONArray entries=new JSONArray(Files.readString(bundle.resolve(manifest)));
      for(int i=0;i<entries.length();i++) {
        JSONObject item=entries.getJSONObject(i);if(item.has("error"))continue;
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(bundle.resolve(item.getString("file")))));
        assertEquals(item.getString("sha256").toLowerCase(Locale.ROOT),hash,"Validation bundle changed: "+item.getString("file"));
      }
    }
    Model support=ModelFactory.createDefaultModel();
    JSONArray imports=new JSONArray(Files.readString(bundle.resolve("imports-manifest.json")));
    for(int i=0;i<imports.length();i++) {
      JSONObject item=imports.getJSONObject(i); if(item.has("error")) continue;
      try(java.io.InputStream stream=Files.newInputStream(bundle.resolve(item.getString("file")))) {
        RDFDataMgr.read(support,stream,item.getString("url"),item.getString("format").equals("xml")?Lang.RDFXML:Lang.TURTLE);
      }
    }
    JSONArray extras=new JSONArray(Files.readString(bundle.resolve("extra-manifest.json")));
    for(int i=0;i<extras.length();i++) {
      JSONObject item=extras.getJSONObject(i);if(item.has("error") || item.getString("format").equals("registry"))continue;
      try(java.io.InputStream stream=Files.newInputStream(bundle.resolve(item.getString("file")))) {RDFDataMgr.read(support,stream,item.getString("url"),item.getString("format").equals("xml")?Lang.RDFXML:Lang.TURTLE);}
    }
    Map<String,Model> profiles=new LinkedHashMap<>();
    profiles.put("core",read(bundle.resolve("shapes.ttl")));
    profiles.put("range",read(bundle.resolve("range.ttl")));
    profiles.put("recommended",read(bundle.resolve("shapes_recommended.ttl")));
    profiles.put("vocabularies",read(bundle.resolve("mdr-vocabularies.shape.ttl")));
    profiles.put("hvd",read(bundle.resolve("hvd-full.ttl")));
    profiles.put("hvd3",read(bundle.resolve("hvd3-full.ttl")));
    Map<String,Model> inputs=new TreeMap<>();
    try(java.util.stream.Stream<Path> paths=Files.list(snapshotRoot)) {
      paths.filter(p->p.getFileName().toString().matches("(dump|repository|source)-[0-9]+\\.rdf")).forEach(p->inputs.put(p.getFileName().toString().replace(".rdf",""),read(p)));
    }
    for(String cid:Arrays.asList("96","118")) {
      if(!Boolean.parseBoolean(System.getProperty("conformance.reconstruct","true"))) {
        inputs.put("reconstructed-"+cid,read(snapshotRoot.resolve("reconstructed-"+cid+".ttl")));continue;
      }
      JSONArray entities=new JSONArray(Files.readString(snapshotRoot.resolve("broker-"+cid+".json")));
      Map<String,JSONObject> byId=new HashMap<>();
      for(int i=0;i<entities.length();i++) {JSONObject e=entities.getJSONObject(i); assertFalse(byId.containsKey(e.getString("id"))); byId.put(e.getString("id"),e);}
      Model reconstructed=ModelFactory.createDefaultModel();DcatApSerializer.nodeResources=new HashMap<>();
      // Reconstruct the catalogue from broker attributes only, never from Idra API.
      // The two live snapshots have no catalogue-level service or record entities.
      JSONObject cat=byId.get("urn:ngsi-ld:Catalogue:id:"+cid);
      if(cat!=null) {
        OdmsCatalogue node=new OdmsCatalogue();node.setId(Integer.parseInt(cid));
        node.setHost(DcatJsonMetadata.first(cat,"source"));node.setHomepage(DcatJsonMetadata.first(cat,"homepage"));
        node.setName(DcatJsonMetadata.first(cat,"title"));node.setDescription(DcatJsonMetadata.first(cat,"description"));
        String publisherId=DcatJsonMetadata.first(cat,"publisher");JSONObject publisher=byId.get(publisherId);
        if(publisher!=null) {node.setPublisherName(DcatJsonMetadata.first(publisher,"name"));node.setPublisherUrl(DcatJsonMetadata.first(publisher,"resourceUri"));}
        DcatApSerializer.nodeResources.put(node.getId(),node);
      }
      for(JSONObject e:byId.values()) if(e.optString("type").equals("Dataset")) {
        DcatDataset d=NgsiDcatReader.dataset(e,cid,byId::get); DcatApSerializer.addDatasetToModel(d,reconstructed);
      }
      try(java.io.OutputStream stream=Files.newOutputStream(root.resolve("reconstructed-"+cid+".ttl"))) {RDFDataMgr.write(stream,reconstructed,Lang.TURTLE);}
      inputs.put("reconstructed-"+cid,reconstructed);
    }
    JSONArray summary=new JSONArray();
    for(Map.Entry<String,Model> input:inputs.entrySet()) {
      String name=input.getKey();Model data=input.getValue(); JSONObject classes=new JSONObject();
      for(String type:Arrays.asList("Catalog","Dataset","Distribution","DatasetSeries","DataService","CatalogRecord")) classes.put(type,data.listSubjectsWithProperty(RDF.type,data.createResource(DCAT.NS+type)).toSet().size());
      classes.put("Agent",data.listSubjectsWithProperty(RDF.type,data.createResource("http://xmlns.com/foaf/0.1/Agent")).toSet().size());
      boolean hvd=data.contains(null,data.createProperty("http://data.europa.eu/r5r/hvdCategory")) || data.contains(null,data.createProperty("http://data.europa.eu/r5r/applicableLegislation"),data.createResource("http://data.europa.eu/eli/reg_impl/2023/138/oj"));
      for(Map.Entry<String,Model> profile:profiles.entrySet()) {
        if(profile.getKey().startsWith("hvd") && !hvd) {summary.put(new JSONObject().put("snapshot",name).put("profile",profile.getKey()).put("status","no declared HVD targets; applicability not inferred").put("classes",classes));continue;}
        // Core/recommended are self-contained. Range/codelists use explicit union,
        // no inferred type from the property's range and no network imports.
        Model validationData=(profile.getKey().equals("core") || profile.getKey().equals("recommended"))?data:ModelFactory.createUnion(data,support);
        ValidationReport report=ShaclValidator.get().validate(profile.getValue().getGraph(),validationData.getGraph());
        Path reportFile=out.resolve(name+"-"+profile.getKey()+".ttl");
        try(java.io.OutputStream stream=Files.newOutputStream(reportFile)) {RDFDataMgr.write(stream,report.getModel(),Lang.TURTLE);}
        JSONArray findings=new JSONArray(); Map<String,Integer> counts=new TreeMap<>();
        Model result=report.getModel(); Property resultProp=result.createProperty(SH+"result");
        result.listObjectsOfProperty(resultProp).forEachRemaining(node->{
          Resource finding=node.asResource();JSONObject row=new JSONObject();
          for(String field:Arrays.asList("focusNode","resultPath","sourceShape","sourceConstraintComponent","resultSeverity","value","resultMessage")) {
            List<String> values=new ArrayList<>();result.listObjectsOfProperty(finding,result.createProperty(SH+field)).forEachRemaining(v->values.add(v.toString()));
            if(!values.isEmpty())row.put(field,new JSONArray(values));
          }
          Statement focusStatement=finding.getProperty(result.createProperty(SH+"focusNode"));
          RDFNode focus=focusStatement==null?null:focusStatement.getObject();
          row.put("focusInSnapshot",focus!=null && focus.isResource() && data.contains(focus.asResource(),null));
          String key=row.optJSONArray("resultPath")+" | "+row.optJSONArray("sourceConstraintComponent")+" | "+row.optJSONArray("resultSeverity");
          counts.merge(key,1,Integer::sum); findings.put(row);
        });
        Files.writeString(out.resolve(name+"-"+profile.getKey()+".json"),findings.toString(2));
        JSONObject coverage=new JSONObject();
        profile.getValue().listObjectsOfProperty(profile.getValue().createProperty(SH+"targetClass")).forEachRemaining(t->{if(t.isURIResource())coverage.put(t.asResource().getURI(),data.listSubjectsWithProperty(RDF.type,t).toSet().size());});
        int corpusFindings=0;for(int i=0;i<findings.length();i++)if(findings.getJSONObject(i).getBoolean("focusInSnapshot"))corpusFindings++;
        summary.put(new JSONObject().put("snapshot",name).put("profile",profile.getKey()).put("conforms",report.conforms()).put("status",data.isEmpty()?"empty graph: no conformance evidence":"validated; see target coverage and dependency limits").put("findings",findings.length()).put("snapshotFocusFindings",corpusFindings).put("supportOnlyFindings",findings.length()-corpusFindings).put("shapeTargetCoverage",coverage).put("groups",counts).put("classes",classes).put("triples",data.size()));
        System.out.println("CONFORMANCE "+name+" "+profile.getKey()+" "+findings.length());
      }
    }
    Files.writeString(out.resolve("summary.json"),summary.toString(2));
  }
}
