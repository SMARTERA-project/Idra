package it.eng.idra.dcat.dump;

import it.eng.idra.beans.odms.OdmsCatalogue;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONObject;

/**
 * Source description of a federated dcat:Catalog, kept beside Idra's own catalogue configuration.
 * Listed members (datasets, services, records, series) are persisted separately and are not captured;
 * links to other catalogues are kept as links only.
 */
public final class DcatCatalogueSource {
  private static final String ROOT="urn:idra:catalogue:source";
  private static final Set<Property> MEMBERS=new HashSet<>(Arrays.asList(DCAT.dataset,DCAT.service,DCAT.record,
      ResourceFactory.createProperty(DCAT.NS+"resource")));
  private static final Set<Resource> STOP=new HashSet<>(Arrays.asList(DCAT.Catalog,DCAT.Dataset,DCAT.DataService,
      DCAT.CatalogRecord,ResourceFactory.createResource(DCAT.NS+"DatasetSeries")));
  private DcatCatalogueSource() { }

  /** Null when the source has no catalogue description of its own. */
  public static String capture(Resource catalogue) {
    Model graph=ModelFactory.createDefaultModel();
    Resource root=graph.createResource(ROOT);
    for(Statement st:catalogue.listProperties().toList()) {
      if(MEMBERS.contains(st.getPredicate()))continue;
      graph.add(root,st.getPredicate(),st.getObject());
      if(st.getObject().isResource())collect(st.getResource(),graph,new HashSet<>(Arrays.asList(catalogue)));
    }
    if(graph.listStatements(root,null,(RDFNode)null).toList().stream().allMatch(st->st.getPredicate().equals(RDF.type)))return null;
    ByteArrayOutputStream out=new ByteArrayOutputStream();RDFDataMgr.write(out,graph,Lang.TURTLE);
    return new JSONObject().put("version",1).put("sourceResource",catalogue.isURIResource()?catalogue.getURI():"[blank-node]")
        .put("capturedAt",java.time.Instant.now().toString()).put("sourceRdf",out.toString(StandardCharsets.UTF_8)).toString();
  }

  private static void collect(Resource r,Model result,Set<Resource> seen) {
    if(!seen.add(r))return;
    // A linked sub-catalogue keeps its own description (DCAT-AP range: dcat:Catalog), not its members;
    // other DCAT resources are persisted on their own.
    boolean catalogue=r.hasProperty(RDF.type,DCAT.Catalog);
    if(!catalogue)for(Resource type:STOP)if(r.hasProperty(RDF.type,type))return;
    for(Statement st:r.listProperties().toList()) {
      if(catalogue&&MEMBERS.contains(st.getPredicate()))continue;
      result.add(st);
      if(st.getObject().isResource())collect(st.getResource(),result,seen);
    }
  }

  /** Source values replace, predicate by predicate, the catalogue values derived from Idra's configuration. */
  public static void write(OdmsCatalogue node,Resource target) {
    String stored=node.getSourceMetadata();
    if(stored==null||stored.isBlank())return;
    JSONObject envelope=new JSONObject(stored);
    if(envelope.getInt("version")!=1)throw new IllegalArgumentException("Unsupported catalogue source version");
    Model source=ModelFactory.createDefaultModel();
    RDFParser.fromString(envelope.getString("sourceRdf")).lang(Lang.TURTLE).parse(source);
    Resource original=source.getResource(ROOT);
    Model model=target.getModel();
    Set<Property> predicates=new LinkedHashSet<>();
    original.listProperties().forEachRemaining(st->predicates.add(st.getPredicate()));
    for(Property p:predicates) {
      if(p.equals(RDF.type))continue;
      List<RDFNode> replaced=model.listObjectsOfProperty(target,p).toList();
      target.removeAll(p);
      for(RDFNode old:replaced)prune(model,old);
    }
    Map<Resource,Resource> blanks=new HashMap<>();
    for(Statement st:source.listStatements().toList()) {
      Resource s=st.getSubject().equals(original)?target:copy(model,st.getSubject(),blanks);
      RDFNode o=st.getObject().isResource()?copy(model,st.getResource(),blanks):st.getObject();
      model.add(s,st.getPredicate(),o);
    }
  }

  private static Resource copy(Model model,Resource r,Map<Resource,Resource> blanks) {
    return r.isAnon()?blanks.computeIfAbsent(r,k->model.createResource()):model.createResource(r.getURI());
  }

  /** Removes a replaced value's description when nothing else refers to it; shared resources are kept. */
  private static void prune(Model model,RDFNode node) {
    if(!node.isResource())return;
    Resource r=node.asResource();
    if(model.listStatements(null,null,r).toList().stream().anyMatch(st->!st.getSubject().equals(r)))return;
    for(Resource type:STOP)if(r.hasProperty(RDF.type,type))return;
    List<RDFNode> children=model.listObjectsOfProperty(r,null).toList();
    model.removeAll(r,null,null);
    for(RDFNode child:children)prune(model,child);
  }
}
