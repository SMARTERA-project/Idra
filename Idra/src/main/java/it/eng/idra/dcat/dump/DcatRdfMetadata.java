package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.utils.DcatDetailsUtil;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

/** DCAT 3 identities and links shared by the EU and IT RDF profiles. */
final class DcatRdfMetadata {
  private static final String DCAT_NS = "http://www.w3.org/ns/dcat#";
  private DcatRdfMetadata() { }
  static List<String> links(Resource resource, String name) {
    List<String> result = new ArrayList<>();
    StmtIterator statements = resource.listProperties(ResourceFactory.createProperty(DCAT_NS + name));
    try {
      while (statements.hasNext()) {
        RDFNode node = statements.next().getObject();
        if (node.isURIResource()) result.add(node.asResource().getURI());
      }
    } finally { statements.close(); }
    return result;
  }
  static void read(DcatDataset target, Resource resource, DcatApDeserializer parser) {
    if (resource.isURIResource()) target.setResourceUri(resource.getURI());
    if(resource.hasProperty(ResourceFactory.createProperty(DCAT_NS+"hasVersion"))) target.setHasVersion(
        it.eng.idra.utils.DcatJsonMetadata.properties(links(resource,"hasVersion"),DCAT_NS+"hasVersion"));
    target.setHasCurrentVersion(links(resource, "hasCurrentVersion"));
    target.setPreviousVersion(links(resource, "previousVersion"));
    target.setPrev(links(resource, "prev"));
    target.setNext(links(resource, "next"));
    List<DcatDatasetSeries> series = new ArrayList<>();
    StmtIterator statements = resource.listProperties(ResourceFactory.createProperty(DCAT_NS + "inSeries"));
    try {
      while (statements.hasNext()) {
        RDFNode node = statements.next().getObject();
        if (!node.isResource()) continue;
        Resource r = node.asResource();
        DcatDatasetSeries s = readSeries(node.asResource(), target.getNodeId(), target.getId(), parser);
        series.add(s);
      }
    } finally { statements.close(); }
    target.setInSeries(series);
    target.setVersionNoteDetails(DcatDetailsUtil.extractKeywordDetails(resource,ResourceFactory.createProperty("http://www.w3.org/ns/adms#versionNotes")));
    Statement version = resource.getProperty(ResourceFactory.createProperty(DCAT_NS + "version"));
    if (version != null && version.getObject().isLiteral() && target.getVersion() != null)
      target.getVersion().setValue(version.getString());
  }
  static DcatDatasetSeries readSeries(Resource r, String nodeId, String datasetId, DcatApDeserializer parser) {
    DcatDatasetSeries s = new DcatDatasetSeries();
    // Blank-node identity is scoped to this acquisition and catalogue.
    s.setResourceUri(r.isURIResource() ? r.getURI() : "urn:idra:series:" + nodeId + ":" + r.getId());
    s.setNodeId(nodeId);
    s.setDatasetId(datasetId);
    s.setTitle(DcatDetailsUtil.extractDatasetDetails(r, DCTerms.title, DCTerms.description));
    s.setDescription(DcatDetailsUtil.extractDatasetDetails(r, DCTerms.title, DCTerms.description));
    for(DcatDetails detail:s.getTitle()) detail.setNodeId(nodeId);
    for(DcatDetails detail:s.getTitle()) detail.setNodeId(nodeId);
    s.setContactPoint(parser.deserializeContactPoint(nodeId, r));
    s.setGeographicalCoverage(parser.deserializeAllSpatialCoverage(nodeId, r));
    DctPeriodOfTime period = parser.deserializeTemporalCoverage(nodeId, r);
    s.setTemporalCoverage(period == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(period)));
    if (r.hasProperty(DCTerms.publisher)) s.setPublisher(parser.deserializeFoafAgent(nodeId, r.getProperty(DCTerms.publisher)));
    List<DcatProperty> laws = new ArrayList<>();
    r.listProperties(DCATAP.applicableLegislation).forEachRemaining(st -> {
      if (st.getObject().isURIResource()) laws.add(new DcatProperty(DCATAP.applicableLegislation, RDFS.Resource, st.getResource().getURI()));
    });
    s.setApplicableLegislation(laws);
    if(r.hasProperty(DCTerms.accrualPeriodicity)) s.setFrequency(new DcatProperty(DCTerms.accrualPeriodicity,RDFS.Resource,r.getProperty(DCTerms.accrualPeriodicity).getObject().toString()));
    if(r.hasProperty(DCTerms.issued)) s.setReleaseDate(new DcatProperty(DCTerms.issued,RDFS.Literal,r.getProperty(DCTerms.issued).getObject().asLiteral().getLexicalForm()));
    if(r.hasProperty(DCTerms.modified)) s.setModificationDate(new DcatProperty(DCTerms.modified,RDFS.Literal,r.getProperty(DCTerms.modified).getObject().asLiteral().getLexicalForm()));
    s.setFirst(links(r, "first")); s.setLast(links(r, "last"));
    s.setPrev(links(r, "prev")); s.setNext(links(r, "next"));
    return s;
  }
  static void addLinks(Resource target, String name, List<String> values) {
    if (values == null) return;
    for (String value : values) if (value != null && !value.trim().isEmpty())
      target.addProperty(ResourceFactory.createProperty(DCAT_NS + name), target.getModel().createResource(value));
  }
  static void writeSeries(DcatDatasetSeries series,Resource r) {
    Model model=r.getModel();
      r.addProperty(RDF.type, ResourceFactory.createResource(DCAT_NS + "DatasetSeries"));
      DcatApSerializer.addMultiLangTitleDescription(series.getTitle(), null, null, r, model);
      DcatApSerializer.addMultiLangTitleDescription(series.getDescription(), null, null, r, model);
      DcatApSerializer.serializeContactPoint(series.getContactPoint(), model, r);
      DcatApSerializer.serializeTemporalCoverage(series.getTemporalCoverage(),model,r);
      DcatApSerializer.serializeSpatialCoverage(series.getGeographicalCoverage(),model,r);
      if(series.getApplicableLegislation()!=null) for(DcatProperty law:series.getApplicableLegislation())
        if(law!=null && law.getValue()!=null && !law.getValue().isEmpty()) r.addProperty(DCATAP.applicableLegislation,model.createResource(law.getValue()));
      if(series.getPublisher()!=null) DcatApSerializer.serializeFoafAgent(series.getPublisher(),model,r);
      if(series.getFrequency()!=null && series.getFrequency().getValue()!=null && !series.getFrequency().getValue().isEmpty()) r.addProperty(DCTerms.accrualPeriodicity,model.createResource(series.getFrequency().getValue()));
      writeDate(r,DCTerms.issued,series.getReleaseDate());
      writeDate(r,DCTerms.modified,series.getModificationDate());
      addLinks(r, "first", series.getFirst()); addLinks(r, "last", series.getLast());
      addLinks(r, "prev", series.getPrev()); addLinks(r, "next", series.getNext());
  }
  static void writeService(DcatDataService service, Resource target) {
    writeProperties(target,DCATAP.applicableLegislation,service.getApplicableLegislation());
    writeProperties(target,DCATAP.hvdCategory,service.getHVDCategory());
    writeProperties(target,DCTerms.rights,service.getRights());
    writeProperties(target,ResourceFactory.createProperty(DCAT_NS+"servesDataset"),service.getServesDataset());
    writeProperties(target,ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/page"),service.getDocumentation());
    if(service.getLicence()!=null)writeProperties(target,DCTerms.license,Arrays.asList(service.getLicence()));
    DcatApSerializer.serializeContactPoint(service.getContactPoint(),target.getModel(),target);
    DcatApSerializer.addMultiLangTitleDescription(service.getDataServiceDetails(),null,null,target,target.getModel());
    if(service.getKeywordDetails()!=null)for(it.eng.idra.beans.dcat.DcatKeyword keyword:service.getKeywordDetails()) {
      if(keyword==null || keyword.getValue()==null || keyword.getValue().isBlank())continue;
      String language=keyword.getLanguage();
      target.addLiteral(DCAT.keyword,language==null||language.isBlank()?target.getModel().createLiteral(keyword.getValue())
          :target.getModel().createLiteral(keyword.getValue(),language));
    }
    writeProperties(target,DCAT.theme,service.getTheme());
    writeProperties(target,DCTerms.conformsTo,service.getConformsTo());
    writeProperties(target,DCAT.landingPage,service.getLandingPage());
    writeProperties(target,DCTerms.format,service.getFormat());
    if(service.getPublisher()!=null)DcatApSerializer.serializeFoafAgent(agent(service.getPublisher(),DCTerms.publisher.getURI()),target.getModel(),target);
  }
  /** Multilingual details, when present, replace the single legacy title. */
  static boolean hasDetails(DcatDataService service) {
    return service.getDataServiceDetails()!=null && !service.getDataServiceDetails().isEmpty();
  }
  /**
   * An agent read back from SQL, Solr or JSON has no (transient) predicate on itself nor on its DcatProperty values;
   * a null predicate aborted the enclosing node. Rebuild it from its stored values for the given predicate.
   */
  static it.eng.idra.beans.dcat.FoafAgent agent(it.eng.idra.beans.dcat.FoafAgent stored,String predicate) {
    java.util.List<String> names=new java.util.ArrayList<>(),languages=new java.util.ArrayList<>();
    if(stored.getName()!=null)for(DcatProperty name:stored.getName())if(name!=null && name.getValue()!=null) {
      names.add(name.getValue());languages.add(name.getLanguage());
    }
    it.eng.idra.beans.dcat.FoafAgent agent=new it.eng.idra.beans.dcat.FoafAgent(predicate,stored.getResourceUri(),names,
        value(stored.getMbox()),value(stored.getHomepage()),value(stored.getType()),value(stored.getIdentifier()),stored.getNodeId());
    agent.setNameLanguages(languages);
    return agent;
  }
  private static String value(DcatProperty property) {return property==null?null:property.getValue();}
  static void writeCatalogue(it.eng.idra.beans.odms.OdmsCatalogue catalogue,Resource target) {
    if(catalogue.getDatasetSeries()!=null) for(DcatDatasetSeries series:catalogue.getDatasetSeries()) {
      Resource r=target.getModel().createResource(series.getResourceUri()!=null ? series.getResourceUri()
          : "urn:idra:series:"+catalogue.getId()+":"+series.getDatasetSeriesId());
      target.addProperty(ResourceFactory.createProperty(DCAT_NS+"resource"),r);
      writeSeries(series,r);
    }
    writeProperties(target,DCATAP.applicableLegislation,catalogue.getApplicableLegislation());
    DcatApSerializer.serializeSpatialCoverage(catalogue.getGeographicalCoverage(),target.getModel(),target);
    DcatApSerializer.serializeTemporalCoverage(catalogue.getTemporalCoverage(),target.getModel(),target);
    if(catalogue.getCreator()!=null) {
      // A creator reloaded from SQL has no (transient) predicate on itself nor on its DcatProperty values; a null
      // predicate aborted the whole catalogue node. Rebuild it from its stored values with dct:creator.
      DcatApSerializer.serializeFoafAgent(agent(catalogue.getCreator(),DCTerms.creator.getURI()),target.getModel(),target);
    }
    if(catalogue.getService()!=null)for(DcatDataService service:catalogue.getService()) {
      String uri=service.getResourceUri()!=null?service.getResourceUri():"urn:idra:service:"+catalogue.getId()+":"+service.getDataServiceId();
      Resource r=target.getModel().createResource(uri,DCAT.DataService);
      target.addProperty(DCAT.service,r);writeService(service,r);
      writeProperties(r,DCAT.endpointURL,service.getEndpointURL());
      writeProperties(r,DCAT.endpointDescription,service.getEndpointDescription());
      if(!hasDetails(service) && service.getTitle()!=null && service.getTitle().getValue()!=null)r.addLiteral(DCTerms.title,service.getTitle().getValue());
    }
    if(catalogue.getRecord()!=null)for(DcatCatalogueRecord record:catalogue.getRecord()) {
      String uri=record.getResourceUri()!=null?record.getResourceUri():"urn:idra:record:"+catalogue.getId()+":"+record.getCatalogueRecordId();
      Resource r=target.getModel().createResource(uri,DCAT.CatalogRecord);target.addProperty(DCAT.record,r);
      DcatApSerializer.addMultiLangTitleDescription(record.getTitle(),null,null,r,target.getModel());
      writeProperties(r,DCTerms.language,record.getLanguages());
      writeDate(r,DCTerms.issued,record.getListingDate());
      if(record.getApplicationProfile()!=null) {
        record.getApplicationProfile().forEach(it.eng.idra.utils.NgsiDcatReader::restoreMetadata);
        DcatApSerializer.serializeDctStandard(record.getApplicationProfile(),r,r.getModel());
      }
      if(record.getChangeType()!=null)writeProperties(r,ResourceFactory.createProperty("http://www.w3.org/ns/adms#status"),Arrays.asList(record.getChangeType()));
      if(record.getSource()!=null)writeProperties(r,DCTerms.source,Arrays.asList(record.getSource()));
      if(record.getPrimaryTopic()!=null)writeProperties(r,ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/primaryTopic"),Arrays.asList(record.getPrimaryTopic()));
      writeDate(r,DCTerms.modified,record.getModificationDate());
    }
  }
  private static void writeProperties(Resource target, org.apache.jena.rdf.model.Property predicate,List<DcatProperty> values) {
    if(values==null)return;
    for(DcatProperty value:values) if(value!=null && value.getValue()!=null && !value.getValue().trim().isEmpty()) {
      if(value.getValue().matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))target.addProperty(predicate,target.getModel().createResource(value.getValue()));
      else target.addLiteral(predicate,value.getValue());
    }
  }
  private static void writeDate(Resource target,org.apache.jena.rdf.model.Property predicate,DcatProperty value) {
    DcatRdfDates.add(target,predicate,value == null ? null : value.getValue());
  }
  static void write(DcatDataset dataset, Resource target) {
    addLinks(target, "hasCurrentVersion", dataset.getHasCurrentVersion());
    addLinks(target, "previousVersion", dataset.getPreviousVersion());
    addLinks(target, "prev", dataset.getPrev()); addLinks(target, "next", dataset.getNext());
    if(dataset.getVersionNoteDetails()!=null && !dataset.getVersionNoteDetails().isEmpty()) {
      org.apache.jena.rdf.model.Property notes=ResourceFactory.createProperty("http://www.w3.org/ns/adms#versionNotes");
      for(DcatKeyword note:dataset.getVersionNoteDetails()) {
        if(note==null || note.getValue()==null) continue;
        target.addLiteral(notes,note.getLanguage()!=null ? target.getModel().createLiteral(note.getValue(),note.getLanguage()) : target.getModel().createLiteral(note.getValue()));
      }
    }
    if (dataset.getInSeries() == null) return;
    for (DcatDatasetSeries series : dataset.getInSeries()) {
      if (series == null) continue;
      String uri = series.getResourceUri();
      if (uri == null || uri.trim().isEmpty()) uri = "urn:idra:series:" + dataset.getNodeId() + ":" + series.getDatasetSeriesId();
      Resource r = target.getModel().createResource(uri);
      target.addProperty(ResourceFactory.createProperty(DCAT_NS + "inSeries"), r);
      writeSeries(series,r);
    }
  }
}
