package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.json.*;
import org.apache.jena.rdf.model.Property;

/** Additive, versioned source evidence; legacy typed fields remain unchanged. */
public final class DcatMetadataEnvelope {
  private static final String ROOT="urn:idra:metadata:root";
  private static final Set<String> CONTROLLED=new HashSet<>(Arrays.asList(
      DCTerms.format.getURI(),DCAT.mediaType.getURI(),DCTerms.language.getURI(),DCTerms.accessRights.getURI()));
  private static final JSONArray RULES=rules();
  private DcatMetadataEnvelope() { }
  private static JSONArray rules() {
    try(InputStream in=DcatMetadataEnvelope.class.getResourceAsStream("/dcat48/mappings.json")) {
      if(in==null)throw new IllegalStateException("Missing pinned mapping configuration");
      return new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    } catch(IOException ex) {throw new IllegalStateException(ex);}
  }
  private static Model parse(String text) {
    Model m=ModelFactory.createDefaultModel();RDFParser.fromString(text).lang(Lang.TURTLE).parse(m);return m;
  }
  private static String turtle(Model m) {
    ByteArrayOutputStream out=new ByteArrayOutputStream();RDFDataMgr.write(out,m,Lang.TURTLE);
    return out.toString(StandardCharsets.UTF_8);
  }
  private static String uri(DcatDataset d) {
    return d.getResourceUri()!=null&&!d.getResourceUri().isBlank()?d.getResourceUri():"urn:idra:dataset:"+d.getNodeId()+":"+d.getId();
  }
  private static Model typed(DcatDataset d) {
    String saved=d.getMetadataEnvelope();
    try {d.setMetadataEnvelope(null);return DcatApSerializer.addDatasetToModel(d,ModelFactory.createDefaultModel());}
    finally {d.setMetadataEnvelope(saved);}
  }
  private static void collect(Resource r, Resource root, Model result, Set<Resource> seen) {
    if(!seen.add(r))return;
    if(!r.equals(root)&&(r.hasProperty(RDF.type,DCAT.Dataset)||r.hasProperty(RDF.type,DCAT.Catalog)))return;
    for(Statement st:r.listProperties().toList()) {
      result.add(st);
      if(st.getObject().isResource())collect(st.getResource(),root,result,seen);
    }
  }
  private static Model rename(Model input,Resource from,Resource to) {
    Model m=ModelFactory.createDefaultModel();
    for(Statement st:input.listStatements().toList()) {
      Resource s=st.getSubject().equals(from)?to:st.getSubject();
      RDFNode o=st.getObject().equals(from)?to:st.getObject();m.add(s,st.getPredicate(),o);
    }
    return m;
  }
  public static void capture(DcatDataset d,Resource source,String provenance) {
    Model graph=ModelFactory.createDefaultModel();collect(source,source,graph,new HashSet<>());
    graph=rename(graph,source,graph.createResource(ROOT));
    Model projection=rename(typed(d),ResourceFactory.createResource(uri(d)),ResourceFactory.createResource(ROOT));
    JSONObject envelope=new JSONObject().put("version",1).put("source",provenance)
        .put("owner",d.getNodeId()).put("sourceResource",source.toString()).put("capturedAt",java.time.Instant.now().toString())
        .put("sourceRdf",turtle(graph)).put("modeledRdf",turtle(projection));
    JSONArray values=mappings(graph,provenance);
    for(Statement st:graph.getResource(ROOT).listProperties(DCAT.theme).toList())if(st.getObject().isAnon()) {
      List<String> labels=new ArrayList<>();
      for(RDFNode label:graph.listObjectsOfProperty(st.getResource(),SKOS.prefLabel).toList())
        if(label.isLiteral())labels.add(label.asLiteral().getLanguage()+"\n"+label.asLiteral().getLexicalForm());
      if(!labels.isEmpty()) {
        Collections.sort(labels);values.put(new JSONObject().put("predicate",DCAT.theme.getURI()).put("subject",ROOT)
            .put("rawLabels",new JSONArray(labels)).put("source",provenance).put("owner",d.getNodeId())
            .put("canonical",localConceptUri(d.getNodeId(),labels.toString(),""))
            .put("status","LOCAL_IDENTIFIER").put("rule","catalogue-language-label-v1").put("skosAssociations",new JSONArray()));
      }
    }
    envelope.put("mappings",values);
    d.setMetadataEnvelope(envelope.toString());
  }
  /** For non-RDF connectors raw values come from their typed source mapping, never historical inference. */
  public static void captureTyped(DcatDataset d,String provenance) {
    for(DcatDistribution dist:d.getDistributions())if(dist.getResourceUri()==null||dist.getResourceUri().isBlank()) {
      String identity=d.getNodeId()+"\n"+d.getIdentifier().getValue()+"\n"+dist.getFormat().getValue()+"\n"+dist.getAccessUrl().getValue();
      dist.setResourceUri("urn:idra:distribution:"+d.getNodeId()+":"+UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
    }
    Model raw=typed(d);Resource root=raw.getResource(uri(d));
    raw.removeAll(root,DCTerms.language,null);
    for(DcatProperty language:d.getLanguage())if(language!=null&&language.getValue()!=null)
      root.addProperty(DCTerms.language,rawTerm(raw,language.getValue()));
    for(DcatDistribution dist:d.getDistributions()) {
      String id=dist.getResourceUri()!=null&&!dist.getResourceUri().isBlank()?dist.getResourceUri():"urn:idra:distribution:"+d.getNodeId()+":"+dist.getId();
      Resource r=raw.getResource(id);
      for(DcatProperty p:Arrays.asList(dist.getFormat(),dist.getMediaType()))if(p!=null&&p.getValue()!=null&&!p.getValue().isBlank()) {
        raw.removeAll(r,p.getProperty(),null);r.addProperty(p.getProperty(),rawTerm(raw,p.getValue()));
      }
    }
    // The intermediate serializer's range assertions are not evidence from a JSON/XML source.
    for(Statement st:raw.listStatements().toList())if(CONTROLLED.contains(st.getPredicate().getURI())&&st.getObject().isURIResource())
      for(Resource range:Arrays.asList(DCTerms.MediaTypeOrExtent,DCTerms.MediaType,DCTerms.LinguisticSystem))raw.removeAll(st.getResource(),RDF.type,range);
    capture(d,root,provenance);
  }
  /** Preserve a non-RDF source's temporal identity/label without using it as a predicate. */
  public static void recordTemporalValue(DcatDataset d,String value) {
    if(value==null||value.isBlank())return;
    JSONObject envelope=new JSONObject(d.getMetadataEnvelope());Model raw=parse(envelope.getString("sourceRdf"));
    Resource root=raw.getResource(ROOT);Resource period=root.getPropertyResourceValue(DCTerms.temporal);
    if(period==null) {period=raw.createResource(DCTerms.PeriodOfTime);root.addProperty(DCTerms.temporal,period);}
    boolean absolute=false;
    try {absolute=new java.net.URI(value).isAbsolute();}catch(java.net.URISyntaxException invalid){ }
    if(absolute)raw=rename(raw,period,raw.createResource(value));
    else period.addProperty(RDFS.label,value);
    envelope.put("sourceRdf",turtle(raw));d.setMetadataEnvelope(envelope.toString());
  }
  public static void recordSourceThemes(DcatDataset d,List<String> themes,List<String> languages) {
    JSONObject envelope=new JSONObject(d.getMetadataEnvelope());
    envelope.put("sourceThemes",themes==null?new JSONArray():new JSONArray(themes));
    envelope.put("sourceLanguages",languages==null?new JSONArray():new JSONArray(languages));
    d.setMetadataEnvelope(envelope.toString());
  }
  private static RDFNode rawTerm(Model model,String value) {
    return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")?model.createResource(value):model.createLiteral(value);
  }
  private static JSONArray mappings(Model graph,String provenance) {
    JSONArray out=new JSONArray();
    for(Statement st:graph.listStatements().toList())if(CONTROLLED.contains(st.getPredicate().getURI())) {
      RDFNode value=st.getObject();String raw=value.isLiteral()?value.asLiteral().getLexicalForm():value.toString();
      JSONObject item=new JSONObject().put("subject",st.getSubject().isAnon()?"[blank-node]":st.getSubject().toString())
          .put("subjectKind",st.getSubject().isAnon()?"BNODE":"IRI").put("scope","dataset-source-graph").put("predicate",st.getPredicate().getURI())
          .put("rawValue",raw).put("termKind",value.isURIResource()?"IRI":value.isLiteral()?"LITERAL":"BNODE")
          .put("source",provenance).put("status",value.isURIResource()?"PRESERVED_IRI":"UNMAPPED");
      if(value.isLiteral())item.put("language",value.asLiteral().getLanguage()).put("datatype",value.asLiteral().getDatatypeURI());
      for(int i=0;i<RULES.length();i++) {
        JSONObject rule=RULES.getJSONObject(i);
        if(st.getPredicate().getURI().equals(rule.getString("predicate"))&&raw.equals(rule.getString("raw"))) {
          item.put("canonical",rule.getString("canonical")).put("status","VERIFIED")
              .put("rule",rule.getString("id")).put("evidence",rule.getJSONObject("evidence"));break;
        }
      }
      out.put(item);
    }
    return out;
  }
  private static String term(RDFNode n,Set<Resource> visiting) {
    if(n.isLiteral())return n.asNode().toString();
    Resource r=n.asResource();if(!visiting.add(r))return "[cycle]";
    List<String> values=new ArrayList<>();
    for(Statement s:r.listProperties().toList())values.add(s.getPredicate().getURI()+"="+term(s.getObject(),visiting));
    String identity=n.isURIResource()?r.getURI():"[]";
    if(identity.startsWith("urn:idra:distribution:"))identity="urn:idra:distribution:[generated]";
    visiting.remove(r);Collections.sort(values);return identity+values.toString();
  }
  private static List<String> signature(Resource r,Property p) {
    List<String> out=new ArrayList<>();
    for(RDFNode n:r.getModel().listObjectsOfProperty(r,p).toList())out.add(term(n,new HashSet<>()));
    Collections.sort(out);return out;
  }
  private static void pruneAnonymous(Model model,RDFNode node) {
    boolean generated=node.isURIResource()&&(Arrays.asList("urn:idra:distribution:","urn:idra:series:","urn:idra:service:")
        .stream().anyMatch(prefix->node.asResource().getURI().startsWith(prefix))
        // A typed location now carries its identifier IRI; once replaced by the source it is a leftover.
        ||model.contains(node.asResource(),RDF.type,DCTerms.Location));
    if((!node.isAnon()&&!generated)||model.contains(null,null,node))return;
    Resource r=node.asResource();List<RDFNode> children=model.listObjectsOfProperty(r,null).toList();
    model.removeAll(r,null,null);for(RDFNode child:children)pruneAnonymous(model,child);
  }
  /** Source structures are authoritative; explicit scalar title/description edits still win. */
  public static void write(DcatDataset d,Resource target) {
    if(d.getMetadataEnvelope()==null||d.getMetadataEnvelope().isBlank())return;
    JSONObject envelope=new JSONObject(d.getMetadataEnvelope());
    if(envelope.getInt("version")!=1)throw new IllegalArgumentException("Unsupported metadata envelope version");
    Model source=parse(envelope.getString("sourceRdf")),before=parse(envelope.getString("modeledRdf"));
    Resource original=source.getResource(ROOT),old=before.getResource(ROOT);
    Model currentClosure=ModelFactory.createDefaultModel();collect(target,target,currentClosure,new HashSet<>());
    Set<Resource> priorVocabulary=new HashSet<>();
    for(Statement st:currentClosure.listStatements().toList())
      if(CONTROLLED.contains(st.getPredicate().getURI())&&st.getObject().isURIResource())priorVocabulary.add(st.getResource());
    Set<Property> applied=new HashSet<>();
    Set<Resource> appliedSubjects=new HashSet<>();
    Set<Property> predicates=new HashSet<>();original.listProperties().forEachRemaining(s->predicates.add(s.getPredicate()));
    for(Property p:predicates) {
      if((p.equals(DCTerms.title)||p.equals(DCTerms.description)||p.equals(DCTerms.identifier)
          ||p.equals(DCTerms.issued)||p.equals(DCTerms.modified))
          &&!signature(target,p).equals(signature(old,p)))continue;
      if(p.equals(RDF.type)) {
        for(RDFNode type:source.listObjectsOfProperty(original,p).toList())target.addProperty(p,type);
        continue;
      }
      applied.add(p);List<RDFNode> replaced=target.getModel().listObjectsOfProperty(target,p).toList();target.removeAll(p);
      for(RDFNode child:replaced)pruneAnonymous(target.getModel(),child);
      Model branch=ModelFactory.createDefaultModel();
      for(Statement st:original.listProperties(p).toList()) {
        branch.add(st);if(st.getObject().isResource())collect(st.getResource(),original,branch,new HashSet<>(Arrays.asList(original)));
      }
      branch=rename(branch,original,target);
      for(Statement st:branch.listStatements().toList())appliedSubjects.add(st.getSubject());
      // Replace each captured predicate as a group, retaining multiple source values.
      // This avoids adding raw literals beside the legacy serializer's synthetic URI.
      for(Statement st:branch.listStatements().toList())if(!st.getPredicate().equals(RDF.type))
        target.getModel().removeAll(st.getSubject(),st.getPredicate(),null);
      for(Statement st:branch.listStatements().toList()) {
        if((st.getPredicate().equals(DCTerms.issued)||st.getPredicate().equals(DCTerms.modified))&&st.getObject().isLiteral())
          DcatRdfDates.add(st.getSubject().inModel(target.getModel()),st.getPredicate(),st.getLiteral().getLexicalForm());
        else target.getModel().add(st);
      }
    }
    // Canonical projection only follows an explicit verified rule. The original RDF stays intact in the envelope.
    JSONArray mappings=envelope.getJSONArray("mappings");
    for(int i=0;i<mappings.length();i++) {
      JSONObject mapping=mappings.getJSONObject(i);
      if(!mapping.optString("status").equals("VERIFIED"))continue;
      boolean configured=false;
      for(int k=0;k<RULES.length();k++) {
        JSONObject rule=RULES.getJSONObject(k);
        if(rule.getString("id").equals(mapping.optString("rule"))&&rule.getString("raw").equals(mapping.optString("rawValue"))
            &&rule.getString("predicate").equals(mapping.optString("predicate"))&&rule.getString("canonical").equals(mapping.optString("canonical")))configured=true;
      }
      if(!configured)throw new IllegalArgumentException("Unverified mapping in metadata envelope");
      String subject=mapping.getString("subject");
      if(subject.equals(ROOT)&&!applied.contains(ResourceFactory.createProperty(mapping.getString("predicate"))))continue;
      Property p=target.getModel().createProperty(mapping.getString("predicate"));
      List<Resource> resources=new ArrayList<>();
      if(mapping.optString("subjectKind").equals("BNODE")) {
        for(Statement st:source.listStatements(null,p,(RDFNode)null).toList())
          if(st.getSubject().isAnon()&&appliedSubjects.contains(st.getSubject()))resources.add(st.getSubject().inModel(target.getModel()));
      } else resources.add(subject.equals(ROOT)?target:target.getModel().getResource(subject));
      // Only replace the exact source term; never normalise unrelated or edited terms.
      for(Resource r:resources)
      for(Statement st:r.listProperties(p).toList()) {
        RDFNode v=st.getObject();String raw=v.isLiteral()?v.asLiteral().getLexicalForm():v.toString();
        if(raw.equals(mapping.getString("rawValue"))) {
          target.getModel().remove(st);r.addProperty(p,target.getModel().createResource(mapping.getString("canonical")));
        }
      }
    }
    // A source protocol/custom IRI does not acquire a vocabulary class just because
    // the legacy typed serializer supplied that property's expected range.
    for(Statement st:source.listStatements().toList())if(CONTROLLED.contains(st.getPredicate().getURI())&&st.getObject().isURIResource()) {
      Resource resource=st.getResource();
      for(Resource range:Arrays.asList(DCTerms.MediaTypeOrExtent,DCTerms.MediaType,DCTerms.LinguisticSystem))
        if(!source.contains(resource,RDF.type,range))target.getModel().removeAll(resource,RDF.type,range);
    }
    for(Resource resource:priorVocabulary)if(!target.getModel().contains(null,null,resource)) {
      List<Statement> properties=target.getModel().listStatements(resource,null,(RDFNode)null).toList();
      if(properties.stream().allMatch(st->st.getPredicate().equals(RDF.type)))target.getModel().removeAll(resource,null,null);
    }
    // Local identifiers express catalogue ownership, not membership of an EU vocabulary.
    for(Statement st:target.listProperties(DCAT.theme).toList())if(st.getObject().isAnon()) {
      Resource theme=st.getResource();List<String> labels=new ArrayList<>();
      for(RDFNode label:theme.getModel().listObjectsOfProperty(theme,SKOS.prefLabel).toList())
        if(label.isLiteral())labels.add(label.asLiteral().getLanguage()+"\n"+label.asLiteral().getLexicalForm());
      if(labels.isEmpty())continue;Collections.sort(labels);
      Resource local=target.getModel().createResource(localConceptUri(envelope.optString("owner",d.getNodeId()),labels.toString(),""));
      for(Statement field:theme.listProperties().toList())local.addProperty(field.getPredicate(),field.getObject());
      target.getModel().remove(st);target.addProperty(DCAT.theme,local);
      pruneAnonymous(target.getModel(),theme);
    }
  }
  public static String localConceptUri(String owner,String label,String language) {
    if(owner==null||owner.isBlank())throw new IllegalArgumentException("Local concept requires catalogue ownership");
    String key=owner+"\n"+(language==null?"":language)+"\n"+label;
    return "urn:idra:concept:"+owner+":"+UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }
}
