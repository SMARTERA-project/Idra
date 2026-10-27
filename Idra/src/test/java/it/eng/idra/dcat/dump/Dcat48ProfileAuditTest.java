package it.eng.idra.dcat.dump;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.shacl.*;
import org.apache.jena.vocabulary.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly versioned audit; source findings are reported, never converted into invented metadata. */
class Dcat48ProfileAuditTest {
  @Test void validatesPinnedEuropeanV2AndRejectsEmptyItalianShapes() throws Exception {
    String value=System.getProperty("plan48.root");if(value==null)return;
    Path root=Path.of(value),profiles=root.resolve("profiles"),reports=root.resolve("profile-reports");Files.createDirectories(reports);
    JSONArray manifest=new JSONArray(Files.readString(profiles.resolve("manifest.json")));
    for(Object item:manifest) {
      JSONObject entry=(JSONObject)item;if(entry.has("error"))continue;
      byte[] bytes=Files.readAllBytes(profiles.resolve(entry.getString("file")));
      assertEquals(entry.getString("sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    Model core=RDFDataMgr.loadModel(profiles.resolve("eu2-dcat-ap_2.1.1_shacl_shapes.ttl").toUri().toString());
    Model positive=ModelFactory.createDefaultModel();Resource dataset=positive.createResource("urn:plan48:positive",DCAT.Dataset);
    dataset.addLiteral(DCTerms.title,"Title").addLiteral(DCTerms.description,"Description");
    assertTrue(ShaclValidator.get().validate(core.getGraph(),positive.getGraph()).conforms());
    dataset.removeAll(DCTerms.title);
    assertFalse(ShaclValidator.get().validate(core.getGraph(),positive.getGraph()).conforms());
    JSONObject national=new JSONObject().put("profile","DCAT-AP_IT").put("status","NOT_VALIDATED_EMPTY_OFFICIAL_SHAPES")
        .put("shapeBytes",Files.size(profiles.resolve("it-rules.shacl"))).put("ontologyPinned",true);
    assertEquals(0,Files.size(profiles.resolve("it-rules.shacl")),"If upstream shapes become available, update the declared validation policy");
    JSONArray summaries=new JSONArray();summaries.put(national);
    Path corpus=root.getParent().resolve("dcat3-conformance");
    for(String cid:Arrays.asList("95","104","105","117")) {
      Model data=RDFDataMgr.loadModel(corpus.resolve("dump-"+cid+".rdf").toUri().toString());
      for(String bundle:Arrays.asList("shapes","range","shapes_recommended","mdr-vocabularies.shape","deprecateduris")) {
        Model rules=RDFDataMgr.loadModel(profiles.resolve("eu2-dcat-ap_2.1.1_shacl_"+bundle+".ttl").toUri().toString());
        ValidationReport report=ShaclValidator.get().validate(rules.getGraph(),data.getGraph());
        Path target=reports.resolve("historical-"+cid+"-"+bundle+".ttl");
        try(java.io.OutputStream out=Files.newOutputStream(target)){RDFDataMgr.write(out,report.getModel(),Lang.TURTLE);}
        long findings=report.getModel().listStatements(null,RDF.type,report.getModel().createResource("http://www.w3.org/ns/shacl#ValidationResult")).toList().size();
        summaries.put(new JSONObject().put("profile","DCAT-AP 2.1.1").put("catalogue",cid).put("snapshot","historical")
            .put("bundle",bundle).put("findings",findings).put("datasetTargets",data.listResourcesWithProperty(RDF.type,DCAT.Dataset).toList().size())
            .put("distributionTargets",data.listResourcesWithProperty(RDF.type,DCAT.Distribution).toList().size())
            .put("imports","OFFLINE_NONE: range/vocabulary outputs are limited to the submitted graph; no general inference"));
      }
    }
    Files.writeString(reports.resolve("summary.json"),summaries.toString(2));
  }
}
