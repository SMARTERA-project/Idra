package it.eng.idra.utils;

import it.eng.idra.beans.dcat.*;
import java.util.*;
import org.json.*;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.*;

/** Shared, lossless handling of DCAT extensions in native and normalized JSON. */
public final class DcatJsonMetadata {
  private DcatJsonMetadata() { }
  private static final Map<Object,String> SOURCES=Collections.synchronizedMap(new WeakHashMap<Object,String>());
  public static void remember(Object bean,String json) { SOURCES.put(bean,json); }
  public static JSONObject source(Object bean) {
    String raw=SOURCES.get(bean);
    com.google.gson.Gson gson=new com.google.gson.GsonBuilder().setExclusionStrategies(new com.google.gson.ExclusionStrategy() {
      public boolean shouldSkipField(com.google.gson.FieldAttributes field) {return false;}
      public boolean shouldSkipClass(Class<?> type) {return type==java.lang.Package.class;}
    }).create();
    JSONObject source=new JSONObject(raw!=null?raw:gson.toJson(bean));
    JSONArray extras=source.optJSONArray("extras");
    if(extras!=null) for(int i=0;i<extras.length();i++) {
      JSONObject extra=extras.optJSONObject(i);if(extra==null)continue;
      String key=extra.optString("key","");if(key.isEmpty())continue;
      Object value=extra.opt("value");
      if(value instanceof String) {
        String text=((String)value).trim();
        if(text.startsWith("[") || text.startsWith("{")) {
          try { value=new JSONTokener(text).nextValue(); } catch(JSONException invalid) { /* Retain the original text. */ }
        }
      }
      source.put(key,value);
    }
    return source;
  }

  public static Object unwrap(Object value) {
    if (value instanceof JSONObject) {
      JSONObject obj = (JSONObject) value;
      if (obj.has("object")) return obj.opt("object");
      if (obj.has("languageMap")) return obj.opt("languageMap");
      if (obj.has("value")) return obj.opt("value");
      if (obj.has("@value")) return obj.opt("@value");
      if (obj.has("@id")) return obj.opt("@id");
    }
    return value;
  }

  public static List<String> strings(Object input) {
    List<String> result = new ArrayList<>();
    collect(input, result);
    return result;
  }
  private static void collect(Object input, List<String> result) {
    Object value = unwrap(input);
    if (value == null || value == JSONObject.NULL) return;
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) collect(array.opt(i), result);
    } else if (value instanceof Iterable<?>) {
      for (Object item : (Iterable<?>) value) collect(item, result);
    } else if (!(value instanceof JSONObject)) {
      String text = String.valueOf(value);
      if (!text.trim().isEmpty()) result.add(text);
    }
  }

  public static Object field(JSONObject obj, String... aliases) {
    for (String alias : aliases) if (obj.has(alias)) return unwrap(obj.opt(alias));
    return null;
  }
  public static JSONObject canonical(JSONObject input) {
    JSONObject source=new JSONObject(input.toString());
    for(String name:input.keySet()) {
      String local=name;
      // Expanded terms from Orion must keep their vocabulary-specific meaning.
      // In particular adms:identifier is not dct:identifier.
      if(name.equals("http://xmlns.com/foaf/0.1/page"))local="documentation";
      if(name.equals("http://www.w3.org/ns/adms#identifier"))local="otherIdentifier";
      if(name.equals("http://www.w3.org/ns/adms#sample"))local="sample";
      if(name.equals("http://www.w3.org/ns/adms#versionNotes"))local="versionNotes";
      for(String namespace:Arrays.asList("https://uri.etsi.org/ngsi-ld/default-context/","https://smartdatamodels.org/dataModel.DCAT-AP/","http://purl.org/dc/terms/","http://www.w3.org/ns/dcat#","http://data.europa.eu/r5r/")) {
        if(local.startsWith(namespace)){local=local.substring(namespace.length());break;}
      }
      StringBuilder converted=new StringBuilder();boolean upper=false;
      for(char c:local.toCharArray()) {if(c=='_')upper=true;else {converted.append(upper?Character.toUpperCase(c):c);upper=false;}}
      String key=converted.toString();
      if(key.equals("endpointUrl"))key="endpointURL";
      if(key.equals("endpointUrls"))key="endpointURL";
      if(key.equals("accessServices"))key="accessService";
      if(!source.has(key))source.put(key,input.opt(name));
    }
    if(source.has("@id") && !source.has("id"))source.put("id",source.opt("@id"));
    return source;
  }
  // Only an absolute IRI identifies the resource: a local id (Roma S003 resources: "uri": "distr340")
  // would become a relative IRI and break the whole catalogue export.
  static String identity(String uri) {
    if (uri == null) return null;
    String value = uri.trim();
    try {
      java.net.URI parsed = new java.net.URI(value);
      return parsed.isAbsolute() && parsed.getScheme().matches("(?i)https?|urn") ? value : null;
    } catch (java.net.URISyntaxException e) {
      return null;
    }
  }

  public static String first(JSONObject obj, String... aliases) {
    List<String> values = strings(field(obj, aliases));
    return values.isEmpty() ? null : values.get(0);
  }

  /** A link without inline description remains a link; it is never copied from its member. */
  public static List<DcatDatasetSeries> series(Object input, String nodeId, String datasetId) {
    List<DcatDatasetSeries> result=new ArrayList<>();
    Object value=unwrap(input);
    if(value==null || value==JSONObject.NULL)return result;
    JSONArray values=value instanceof JSONArray?(JSONArray)value:new JSONArray().put(value);
    for(int i=0;i<values.length();i++) {
      Object item=values.opt(i);
      JSONObject source=item instanceof JSONObject?(JSONObject)item:new JSONObject().put("resourceUri",item);
      result.add(NgsiDcatReader.series(source,nodeId,datasetId));
    }
    return result;
  }

  public static void apply(DcatDataset target, JSONObject source) {
    source=canonical(source);
    String uri = identity(first(source, "resourceUri", "@id", "uri"));
    if (uri != null) target.setResourceUri(uri);
    String evidence=first(source,"metadataEnvelope");
    if(evidence!=null)target.setMetadataEnvelope(evidence);
    if (source.has("inSeries") || source.has("in_series"))
      target.setInSeries(series(field(source, "inSeries", "in_series"), target.getNodeId(), target.getId()));
    if (source.has("hasCurrentVersion")) target.setHasCurrentVersion(strings(field(source, "hasCurrentVersion")));
    if (source.has("previousVersion")) target.setPreviousVersion(strings(field(source, "previousVersion")));
    if (source.has("prev")) target.setPrev(strings(field(source, "prev")));
    if (source.has("next")) target.setNext(strings(field(source, "next")));
    Object hvd = field(source, "hvdCategory", "HVDCategory", "hvd_category");
    if (hvd != null) target.setHVDCategory(properties(hvd, DCATAP.hvdCategory.getURI()));
    Object law = field(source, "applicableLegislation", "applicable_legislation");
    if (law != null) target.setApplicableLegislation(properties(law, DCATAP.applicableLegislation.getURI()));
    Object activity = field(source, "wasGeneratedBy", "was_generated_by");
    if (activity != null) target.setWasGeneratedBy(properties(activity, "http://www.w3.org/ns/prov#wasGeneratedBy"));
    String duration = first(source, "temporalResolution", "temporal_resolution");
    if (duration != null) target.setTemporalResolution(new DcatProperty(DCAT.temporalResolution, XSD.duration, duration));
    String version = first(source, "version", "versionInfo");
    if (version != null && target.getVersion() != null) target.getVersion().setValue(version);
    if(source.has("versionNotes"))target.setVersionNoteDetails(DcatDetailsUtil.extractKeywordDetails(source.opt("versionNotes")));
    Object relation=field(source,"qualifiedRelation","qualified_relation");
    if(relation!=null) {
      List<Relationship> list=new ArrayList<>();JSONArray array=relation instanceof JSONArray?(JSONArray)relation:new JSONArray().put(relation);
      for(int i=0;i<array.length();i++) {
        JSONObject item=array.optJSONObject(i);if(item==null)continue;
        list.add(new Relationship(first(item,"hadRole","had_role","role"),first(item,"relation"),target.getNodeId()));
      }
      target.setQualifiedRelation(list);
    }
  }

  public static List<DcatProperty> properties(Object value, String predicate) {
    List<DcatProperty> result = new ArrayList<>();
    for (String text : strings(value)) result.add(new DcatProperty(ResourceFactory.createProperty(predicate), RDFS.Resource, text));
    return result;
  }
  public static void apply(DcatDistribution target,JSONObject source) {
    String uri=identity(first(source,"resourceUri","@id","uri"));if(uri!=null)target.setResourceUri(uri);
    Object legislation=field(source,"applicableLegislation","applicable_legislation");
    if(legislation!=null)target.setApplicableLegislation(properties(legislation,DCATAP.applicableLegislation.getURI()));
    String v=first(source,"availability");if(v!=null)target.setAvailability(new DcatProperty(DCATAP.availability,RDFS.Resource,v));
    v=first(source,"compressFormat","compressionFormat","compress_format");if(v!=null)target.setCompressionFormat(new DcatProperty(DCAT.compressFormat,RDFS.Resource,v));
    v=first(source,"packageFormat","packagingFormat","package_format");if(v!=null)target.setPackagingFormat(new DcatProperty(DCAT.packageFormat,RDFS.Resource,v));
    v=first(source,"hasPolicy","has_policy");if(v!=null)target.setHasPolicy(new DcatProperty(ResourceFactory.createProperty("http://www.w3.org/ns/odrl/2/hasPolicy"),RDFS.Resource,v));
    v=first(source,"temporalResolution","temporal_resolution");if(v!=null)target.setTemporalResolution(new DcatProperty(DCAT.temporalResolution,XSD.duration,v));
    v=first(source,"spatialResolutionInMeters","spatialResolution","spatial_resolution_in_meters");if(v!=null)target.setSpatialResolution(new DcatProperty(DCAT.spatialResolutionInMeters,XSD.decimal,v));
    Object services=field(source,"accessService","access_services");
    if(services instanceof String)services=((String)services).trim().startsWith("[")?new JSONArray((String)services):null;
    if(services!=null) {
      List<DcatDataService> list=new ArrayList<>();JSONArray array=services instanceof JSONArray?(JSONArray)services:new JSONArray().put(services);
      for(int i=0;i<array.length();i++) if(array.optJSONObject(i)!=null)list.add(NgsiDcatReader.service(array.getJSONObject(i),target.getNodeId()));
      target.setAccessService(list);
    }
  }
}
