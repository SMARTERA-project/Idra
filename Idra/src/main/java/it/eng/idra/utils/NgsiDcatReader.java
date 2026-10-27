package it.eng.idra.utils;

import com.google.gson.Gson;
import it.eng.idra.beans.dcat.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import org.json.*;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;

/** Reads normalized NGSI-LD and legacy keyValues using the same typed DCAT model. */
public final class NgsiDcatReader {
  private static final Gson GSON = new Gson();
  private NgsiDcatReader() { }
  private static List<Field> fields(Class<?> type) {
    List<Field> fields=new ArrayList<>();
    for(Class<?> c=type;c!=null && c!=Object.class;c=c.getSuperclass()) fields.addAll(Arrays.asList(c.getDeclaredFields()));
    return fields;
  }
  public static void contextHeaders(Map<String,String> headers) {
    String url=PropertyManager.getProperty(it.eng.idra.beans.IdraProperty.NGSI_DCAT_CONTEXT_URL);
    if(url!=null && !url.trim().isEmpty()) headers.put("Link","<"+url+">; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\"");
  }
  private static String sourceName(String field) {
    switch(field) {
      case "HVDCategory": return "hvdCategory";
      case "type": return "resourceType";
      case "licence": return "license";
      case "distributions": return "datasetDistribution";
      case "keywords": return "keyword";
      case "languages": return "language";
      case "accessUrl": return "accessURL";
      case "downloadUrl": return "downloadURL";
      case "compressionFormat": return "compressFormat";
      case "packagingFormat": return "packageFormat";
      case "spatialResolution": return "spatialResolutionInMeters";
      case "spatialCoverage": case "geographicalCoverage": return "spatial";
      case "temporalCoverage": return "temporal";
      case "releaseDate": return "dateCreated";
      case "applicationProfile": return "conformsTo";
      case "listingDate": return "dateCreated";
      case "changeType": return "status";
      case "updateDate": case "modificationDate": return "dateModified";
      default: return field;
    }
  }
  private static String predicate(String field) {
    if(field.equals("primaryTopic"))return "http://xmlns.com/foaf/0.1/primaryTopic";
    if(field.equals("applicationProfile"))return "http://purl.org/dc/terms/conformsTo";
    if(field.equals("listingDate"))return "http://purl.org/dc/terms/issued";
    if(field.equals("changeType"))return "http://www.w3.org/ns/adms#status";
    if(field.equals("languages"))return "http://purl.org/dc/terms/language";
    if(field.equals("documentation"))return "http://xmlns.com/foaf/0.1/page";
    if(field.equals("otherIdentifier"))return "http://www.w3.org/ns/adms#identifier";
    if(field.equals("sample"))return "http://www.w3.org/ns/adms#sample";
    if(field.equals("versionNotes"))return "http://www.w3.org/ns/adms#versionNotes";
    if(field.equals("frequency"))return "http://purl.org/dc/terms/accrualPeriodicity";
    if(field.equals("relatedResource"))return "http://purl.org/dc/terms/relation";
    if(field.equals("algorithm") || field.equals("checksumValue"))return "http://spdx.org/rdf/terms#"+field;
    if(field.equals("wasGeneratedBy")) return "http://www.w3.org/ns/prov#wasGeneratedBy";
    if(field.equals("had_role")) return "http://www.w3.org/ns/dcat#hadRole";
    if(field.equals("licence")) return "http://purl.org/dc/terms/license";
    if(field.equals("geometry")) return "http://www.w3.org/ns/locn#geometry";
    if(field.equals("beginning")) return "http://www.w3.org/2006/time#hasBeginning";
    if(field.equals("end")) return "http://www.w3.org/2006/time#hasEnd";
    if(field.equals("fn") || field.equals("hasEmail") || field.equals("hasUrl") || field.equals("hasTelephoneValue")) return "http://www.w3.org/2006/vcard/ns#"+(field.equals("hasUrl")?"hasURL":field.equals("hasTelephoneValue")?"value":field);
    if(field.equals("hasPolicy")) return "http://www.w3.org/ns/odrl/2/hasPolicy";
    if(field.equals("HVDCategory") || field.equals("applicableLegislation") || field.equals("availability"))
      return "http://data.europa.eu/r5r/"+sourceName(field);
    if(Arrays.asList("version","byteSize","mediaType","compressionFormat","packagingFormat","endpointURL","endpointDescription","servesDataset","startDate","endDate","bbox","centroid","temporalResolution","spatialResolution","accessUrl","downloadUrl","landingPage").contains(field))
      return "http://www.w3.org/ns/dcat#"+sourceName(field);
    if(field.equals("releaseDate")) return "http://purl.org/dc/terms/issued";
    if(field.equals("updateDate")) return "http://purl.org/dc/terms/modified";
    return "http://purl.org/dc/terms/"+field;
  }
  private static Object resolve(Object value, Class<?> type, Function<String,JSONObject> resolver) {
    if(value instanceof String && SkosConcept.class.isAssignableFrom(type)) {
      String text=(String)value;
      if(text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return new JSONObject().put("resourceUri",text);
      return new JSONObject().put("prefLabel",new JSONArray().put(new JSONObject().put("value",text)));
    }
    if(value instanceof String && type.equals(DctLicenseDocument.class)) return new JSONObject().put("uri",value);
    if(value instanceof String && !type.equals(String.class) && !type.equals(DcatProperty.class)) {
      JSONObject linked=resolver.apply((String)value);
      if(linked!=null) return linked;
      return new JSONObject().put("resourceUri",value);
    }
    return value;
  }
  private static String decodeEvidence(Object raw) {
    if(raw instanceof String) return (String)raw;
        JSONObject transport=(JSONObject)raw;
        String encoding=transport.getString("encoding");
        if(!encoding.equals("base64-utf8-chunks-v1") && !encoding.equals("gzip-base64-utf8-chunks-v1"))
          throw new IllegalArgumentException("Unsupported metadata envelope encoding");
        JSONArray chunks=transport.getJSONArray("chunks");StringBuilder encoded=new StringBuilder();
        for(int i=0;i<chunks.length();i++)encoded.append(chunks.getString(i));
        byte[] bytes=java.util.Base64.getDecoder().decode(encoded.toString());
        if(encoding.equals("gzip-base64-utf8-chunks-v1")) {
          try(java.util.zip.GZIPInputStream gzip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
            bytes=gzip.readAllBytes();
          } catch(java.io.IOException error) {throw new IllegalArgumentException("Invalid compressed metadata evidence",error);}
        }
        String decoded=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        // Detect corruption before passing the envelope to persistence or the RDF writer.
        new JSONObject(decoded);
        return decoded;
  }

  /** Reconstruct the acquired no-Dataset graph from normalized catalogue evidence. */
  public static org.apache.jena.rdf.model.Model standaloneCatalogueGraph(JSONObject entity) {
    Object raw=DcatJsonMetadata.field(DcatJsonMetadata.canonical(entity),"metadataEnvelope");
    if(raw==null) throw new IllegalArgumentException("Catalogue has no standalone source evidence");
    String rdf=new JSONObject(decodeEvidence(raw)).getString("sourceRdf");
    org.apache.jena.rdf.model.Model model=org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
    org.apache.jena.riot.RDFParser.fromString(rdf).lang(org.apache.jena.riot.Lang.TURTLE).parse(model);
    return model;
  }

  private static JSONObject nativeJson(JSONObject source,Class<?> type,Function<String,JSONObject> resolver) {
    source=DcatJsonMetadata.canonical(source);
    JSONObject result=new JSONObject();
    for(Field field:fields(type)) {
      if(Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
      String name=field.getName();
      Object raw=DcatJsonMetadata.field(source,sourceName(name),name,predicate(name));
      if(name.equals("metadataEnvelope") && raw instanceof JSONObject
          && ((JSONObject)raw).has("encoding")) {
        raw=decodeEvidence(raw);
      }
      if(name.equals("type") && source.has("id") && source.opt("type") instanceof String
          && Arrays.asList("Dataset","DistributionDCAT-AP","CatalogueDCAT-AP","AgentDCAT-AP","DatasetSeries","DataServiceDCAT-AP","CatalogueRecordDCAT-AP").contains(source.optString("type")))
        raw=DcatJsonMetadata.field(source,"resourceType");
      Class<?> target=field.getType();
      if(target.equals(DcatProperty.class)) {
        String text=scalar(raw); result.put(name,new JSONObject().put("value",text!=null?text:""));
      } else if(List.class.isAssignableFrom(target)) {
        if((type.equals(DcatDatasetSeries.class) || type.equals(DcatCatalogueRecord.class)) && (name.equals("title") || name.equals("description"))) {
          Object original=source.opt(name);
          boolean nativeDetails=raw instanceof JSONArray && ((JSONArray)raw).length()>0
              && ((JSONArray)raw).optJSONObject(0)!=null
              && (((JSONArray)raw).getJSONObject(0).has("title") || ((JSONArray)raw).getJSONObject(0).has("description"));
          if(!nativeDetails) {
            List<DcatDetails> details=type.equals(DcatCatalogueRecord.class) && name.equals("title")
                ? DcatDetailsUtil.extractDatasetDetails(source.opt("title"),source.opt("description"))
                : name.equals("title")?DcatDetailsUtil.extractDatasetDetails(original,null):DcatDetailsUtil.extractDatasetDetails(null,original);
            result.put(name,new JSONArray(GSON.toJson(details))); continue;
          }
        }
        JSONArray values=new JSONArray();
        if(raw!=null) {
          Type t=((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
          Class<?> element=t instanceof Class<?>?(Class<?>)t:String.class;
          JSONArray input=raw instanceof JSONArray?(JSONArray)raw:new JSONArray().put(raw);
          for(int i=0;i<input.length();i++) {
            Object v=element.equals(DcatProperty.class) || element.equals(String.class)
                ? DcatJsonMetadata.unwrap(input.opt(i)) : input.opt(i);
            if(element.equals(DcatProperty.class)) {
              String text=scalar(v); if(text!=null) values.put(new JSONObject().put("value",text));
            } else if(element.equals(String.class)) {
              for(String text:DcatJsonMetadata.strings(v)) values.put(text);
            } else {
              v=resolve(v,element,resolver);
              if(v instanceof JSONObject) values.put(nativeJson((JSONObject)v,element,resolver));
            }
          }
        }
        result.put(name,values);
      } else if(raw!=null && target.getPackage()!=null && target.getPackage().getName().equals("it.eng.idra.beans.dcat")) {
        Object linked=resolve(raw,target,resolver);
        if(linked instanceof JSONObject) result.put(name,nativeJson((JSONObject)linked,target,resolver));
      } else if(raw!=null) result.put(name,raw);
      com.google.gson.annotations.SerializedName serialized=field.getAnnotation(com.google.gson.annotations.SerializedName.class);
      if(serialized!=null && !serialized.value().equals(name) && result.has(name))
        result.put(serialized.value(),result.remove(name));
    }
    if((type.equals(DcatDatasetSeries.class) || type.equals(DcatDataService.class)) && source.has("id") && source.has("type")) result.put("brokerId",source.getString("id"));
    if(type.equals(DcatDistribution.class) && (result.optJSONArray("distributionDetails")==null || result.getJSONArray("distributionDetails").length()==0)) {
      // Distributions resolved inside a dataset never pass through distribution(): keep every language variant.
      List<DcatDetails> details=DcatDetailsUtil.extractDatasetDetails(source.opt("title"),source.opt("description"));
      if(!details.isEmpty()) result.put("distributionDetails",new JSONArray(GSON.toJson(details)));
    }
    if(type.equals(FoafAgent.class)) {
      // Agent names reach the broker as a language map: keep each tag in the parallel list.
      List<DcatDetails> localized=DcatDetailsUtil.extractDatasetDetails(source.opt("name"),null,null,null);
      if(localized.stream().anyMatch(d -> d.getLanguage()!=null && !d.getLanguage().isBlank() && !d.getLanguage().equals("@none"))) {
        JSONArray names=new JSONArray(),languages=new JSONArray();
        for(DcatDetails d:localized) {
          if(d.getTitle()==null || d.getTitle().isBlank()) continue;
          names.put(new JSONObject().put("value",d.getTitle()));
          languages.put(d.getLanguage()==null || d.getLanguage().equals("@none") ? "" : d.getLanguage());
        }
        result.put("name",names);result.put("nameLanguage",languages);
      }
    }
    if(type.equals(DcatDataService.class)) {
      // The broker carries language maps; the bean keeps them as details, like distributions.
      List<DcatDetails> details=DcatDetailsUtil.extractDatasetDetails(source.opt("title"),source.opt("description"));
      if(!details.isEmpty()) result.put("dataServiceDetails",new JSONArray(GSON.toJson(details)));
      List<DcatKeyword> keywords=DcatDetailsUtil.extractKeywordDetails(source.opt("keyword"));
      if(!keywords.isEmpty()) result.put("keywordDetails",new JSONArray(GSON.toJson(keywords)));
    }
    return result;
  }
  private static String scalar(Object raw) {
    List<String> strings=DcatJsonMetadata.strings(raw);
    if(!strings.isEmpty()) return strings.get(0);
    if(raw instanceof JSONObject) {
      JSONObject map=(JSONObject)raw;
      for(String language:map.keySet()) {
        strings=DcatJsonMetadata.strings(map.opt(language));
        if(!strings.isEmpty()) return strings.get(0);
      }
    }
    return null;
  }
  private static void initialize(Object bean) {
    if(bean==null) return;
    for(Field field:fields(bean.getClass())) {
      if(Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
      try {
        field.setAccessible(true); Object value=field.get(bean);
        if(value instanceof FoafAgent) {
          initialize(value);
          FoafAgent agent=(FoafAgent)value;
          if(agent.getPropertyUri()==null || agent.getPropertyUri().isBlank())agent.setPropertyUri(predicate(field.getName()));
        } else if(value instanceof DcatProperty) {
          initializeProperty(bean,field.getName(),(DcatProperty)value);
        } else if(value instanceof List<?>) {
          for(Object child:(List<?>)value) if(child instanceof DcatProperty) {
            initializeProperty(bean,field.getName(),(DcatProperty)child);
          } else if(child!=null && child.getClass().getPackage()!=null
              && child.getClass().getPackage().getName().equals("it.eng.idra.beans.dcat")) {
            initialize(child);
            if(child instanceof SkosConcept) ((SkosConcept)child).setPropertyUri(field.getName().equals("theme")
                ? "http://www.w3.org/ns/dcat#theme" : "http://purl.org/dc/terms/"+field.getName());
          }
        } else if(value!=null && value.getClass().getPackage()!=null
            && value.getClass().getPackage().getName().equals("it.eng.idra.beans.dcat")) initialize(value);
      } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
    }
  }
  private static void initializeProperty(Object bean,String field,DcatProperty property) {
    if(property.getProperty()!=null)return;
    String uri=predicate(field);
    if((bean instanceof FoafAgent || bean instanceof DctLicenseDocument) && field.equals("name"))uri="http://xmlns.com/foaf/0.1/name";
    if(field.equals("geographicalName"))uri="http://www.w3.org/2000/01/rdf-schema#label";
    if(field.equals("geographicalIdentifier"))uri="http://purl.org/dc/terms/identifier";
    if(field.equals("hasTelephoneType"))uri="http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    org.apache.jena.rdf.model.Resource range=RDFS.Resource;
    if(Arrays.asList("title","description","identifier","releaseDate","updateDate","version","versionNotes","byteSize","temporalResolution","spatialResolution","startDate","endDate","bbox","centroid","geographicalName","geographicalIdentifier","checksumValue").contains(field))range=RDFS.Literal;
    if(field.equals("documentation"))range=ResourceFactory.createResource("http://xmlns.com/foaf/0.1/Document");
    if(field.equals("otherIdentifier"))range=ResourceFactory.createResource("http://www.w3.org/ns/adms#Identifier");
    if(field.equals("accessRights") || field.equals("rights"))range=org.apache.jena.vocabulary.DCTerms.RightsStatement;
    if(field.equals("frequency"))range=org.apache.jena.vocabulary.DCTerms.Frequency;
    if(field.equals("format"))range=org.apache.jena.vocabulary.DCTerms.MediaTypeOrExtent;
    if(field.equals("mediaType") || field.equals("compressionFormat") || field.equals("packagingFormat"))range=org.apache.jena.vocabulary.DCTerms.MediaType;
    if(field.equals("language"))range=org.apache.jena.vocabulary.DCTerms.LinguisticSystem;
    if(field.equals("applicableLegislation"))range=it.eng.idra.beans.dcat.ELI.LegalResource;
    property.setProperty(ResourceFactory.createProperty(uri));property.setUri(uri);property.setRange(range);
  }
  /** Restore transient Jena vocabulary objects after JPA/Gson has loaded their values. */
  public static void restoreMetadata(Object bean) {initialize(bean);}
  public static DcatDataset dataset(JSONObject source,String nodeId,Function<String,JSONObject> resolver) {
    source=DcatJsonMetadata.canonical(source);
    DcatDataset result=GSON.fromJson(nativeJson(source,DcatDataset.class,resolver).toString(),DcatDataset.class);
    initialize(result);result.setNodeId(nodeId);result.setId(source.getString("id"));
    if(result.getResourceUri()==null) result.setResourceUri(source.getString("id"));
    result.setDatasetDetails(DcatDetailsUtil.extractDatasetDetails(source.opt("title"),source.opt("description")));
    if(source.optJSONArray("datasetDetails")!=null) result.setDatasetDetails(GSON.fromJson(source.getJSONArray("datasetDetails").toString(),new com.google.gson.reflect.TypeToken<List<DcatDetails>>(){}.getType()));
    result.setKeywordDetails(DcatDetailsUtil.extractKeywordDetails(source.opt("keyword")));
    result.setVersionNoteDetails(DcatDetailsUtil.extractKeywordDetails(source.opt("versionNotes")));
    if (result.getInSeries()!=null) for (DcatDatasetSeries series:result.getInSeries()) {
      series.setNodeId(nodeId);series.setDatasetId(result.getId());
    }
    if(result.getIdentifier()!=null && result.getIdentifier().getValue().isEmpty()) result.getIdentifier().setValue(source.getString("id"));
    return result;
  }
  public static DcatDistribution distribution(JSONObject source,String nodeId,Function<String,JSONObject> resolver) {
    source=DcatJsonMetadata.canonical(source);
    DcatDistribution result=GSON.fromJson(nativeJson(source,DcatDistribution.class,resolver).toString(),DcatDistribution.class);
    initialize(result);result.setNodeId(nodeId);result.setId(source.getString("id"));
    if(result.getIdentifier()==null || result.getIdentifier().getValue().isEmpty()) result.setIdentifier(source.getString("id"));
    result.setDistributionDetails(DcatDetailsUtil.extractDatasetDetails(source.opt("title"),source.opt("description")));
    return result;
  }
  public static DcatDataService service(JSONObject source,String nodeId) {
    DcatDataService result=GSON.fromJson(nativeJson(source,DcatDataService.class,id->null).toString(),DcatDataService.class);
    initialize(result);result.setNodeId(nodeId);return result;
  }
  public static DcatCatalogueRecord record(JSONObject source,String nodeId) {
    source=DcatJsonMetadata.canonical(source);
    DcatCatalogueRecord result=GSON.fromJson(nativeJson(source,DcatCatalogueRecord.class,id->null).toString(),DcatCatalogueRecord.class);
    initialize(result);result.setNodeId(nodeId);
    if(result.getResourceUri()==null)result.setResourceUri(DcatJsonMetadata.first(source,"@id","id"));
    result.getTitle().forEach(detail -> detail.setNodeId(nodeId));
    return result;
  }
  public static DcatDatasetSeries series(JSONObject source,String nodeId,String datasetId) {
    DcatDatasetSeries result=GSON.fromJson(nativeJson(source,DcatDatasetSeries.class,id->null).toString(),DcatDatasetSeries.class);
    initialize(result);result.setNodeId(nodeId);result.setDatasetId(datasetId);
    if(result.getResourceUri()==null)result.setResourceUri(DcatJsonMetadata.first(source,"@id","id"));
    result.getTitle();return result;
  }
}
