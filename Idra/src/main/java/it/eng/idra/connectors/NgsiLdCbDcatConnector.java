/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2025 Engineering Ingegneria Informatica S.p.A.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

package it.eng.idra.connectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import it.eng.idra.beans.IdraProperty;
import it.eng.idra.beans.dcat.DCATAP;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.dcat.DcatDatasetSeries;
import it.eng.idra.beans.dcat.DcatDetails;
import it.eng.idra.beans.dcat.DcatDistribution;
import it.eng.idra.beans.dcat.DcatProperty;
import it.eng.idra.beans.dcat.DctLicenseDocument;
import it.eng.idra.beans.dcat.DctLocation;
import it.eng.idra.beans.dcat.DctPeriodOfTime;
import it.eng.idra.beans.dcat.DctStandard;
import it.eng.idra.beans.dcat.FoafAgent;
import it.eng.idra.beans.dcat.Relationship;
import it.eng.idra.beans.dcat.SkosConcept;
import it.eng.idra.beans.dcat.SkosConceptSubject;
import it.eng.idra.beans.dcat.SkosConceptTheme;
import it.eng.idra.beans.dcat.SkosPrefLabel;
import it.eng.idra.beans.dcat.VcardOrganization;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.beans.odms.OdmsSynchronizationResult;
import it.eng.idra.management.FederationCore;
import it.eng.idra.utils.CommonUtil;
import it.eng.idra.utils.DcatDetailsUtil;
import it.eng.idra.utils.GsonUtil;
import it.eng.idra.utils.GsonUtilException;
import it.eng.idra.utils.PropertyManager;
import it.eng.idra.utils.restclient.RestClient;
import it.eng.idra.utils.restclient.RestClientImpl;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// TODO: Auto-generated Javadoc
/**
 * The Class OrionConnector.
 */
public class NgsiLdCbDcatConnector implements IodmsConnector {

  /** The subscription base callback url. */
  private static String subscriptionBaseUrl = PropertyManager.getProperty(IdraProperty.IDRA_SERVER_BASEURL);

  /** The node id. */
  private String nodeId;

  /** The datasets array. */
  private JSONArray datasetsArray;
  private static final String DATASET_TYPES = java.net.URLEncoder.encode("Dataset,https://uri.etsi.org/ngsi-ld/default-context/Dataset,https://smartdatamodels.org/dataModel.DCAT-AP/Dataset", java.nio.charset.StandardCharsets.UTF_8);
  private final java.util.Map<String,JSONObject> resolvedEntities = new java.util.HashMap<>();

  /** The node. */
  private OdmsCatalogue node;

  /** The logger. */
  private static Logger logger = LogManager.getLogger(NgsiLdCbDcatConnector.class);

  /**
   * Instantiates a new orion connector.
   */
  public NgsiLdCbDcatConnector() {
  }

  /**
   * Instantiates a new orion connector.
   *
   * @param node the node
   */
  public NgsiLdCbDcatConnector(OdmsCatalogue node) {
    if (node.getApiKey() == null || node.getApiKey().equals("")) {
      node.setApiKey(UUID.randomUUID().toString());
    }
    // logger.info("Api settata: " + node.getApiKey());
    this.node = node;
    this.nodeId = String.valueOf(node.getId());
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#findDatasets(java.util.HashMap)
   */
  @Override
  public List<DcatDataset> findDatasets(HashMap<String, Object> searchParameters) throws Exception {
    ArrayList<DcatDataset> resultDatasets = new ArrayList<DcatDataset>();
    return resultDatasets;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * it.eng.idra.connectors.IodmsConnector#countSearchDatasets(java.util.HashMap)
   */
  @Override
  public int countSearchDatasets(HashMap<String, Object> searchParameters) throws Exception {
    return 0;
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#countDatasets()
   */
  @Override
  public int countDatasets() throws Exception {
    // NGSI-LD count=true&limit=0 returns only the NGSILD-Results-Count header.
    Map<String, String> headers = new HashMap<String, String>();
    it.eng.idra.utils.NgsiDcatReader.contextHeaders(headers);
    headers.put("Content-Type", "application/json");
    RestClient client = new RestClientImpl();
    HttpResponse response = client.sendGetRequest(
        node.getHost() + "/ngsi-ld/v1/entities?type=" + DATASET_TYPES + "&count=true&limit=0", headers);
    org.apache.http.Header countHeader = response.getFirstHeader("NGSILD-Results-Count");
    // Always consume the body so the connection goes back to the pool.
    client.getHttpResponseBody(response);
    if (countHeader != null && StringUtils.isNotBlank(countHeader.getValue())) {
      return Integer.parseInt(countHeader.getValue().trim());
    }
    // Broker without count support: count the raw entities without converting them.
    return getJsonDatasets().length();
  }

  /** An empty broker is online: the count request succeeding is enough. */
  @Override
  public it.eng.idra.beans.odms.OdmsCatalogueState checkState() throws Exception {
    countDatasets();
    return it.eng.idra.beans.odms.OdmsCatalogueState.ONLINE;
  }

  /**
   * Gets a dcat distribution.
   *
   * @return the dcat distribution
   * 
   * @throws Exception exception
   */
  public DcatDistribution distributionToDcat(Object distribution,
      OdmsCatalogue node) throws Exception {
    return it.eng.idra.utils.NgsiDcatReader.distribution((JSONObject)distribution, nodeId, id -> {
      try { return getJsonDistribution(id); }
      catch (Exception e) { throw new IllegalStateException("Cannot resolve NGSI-LD entity " + id, e); }
    });
  }

  /**
   * Extract value list.
   *
   * @param value the value
   * @return the list
   */
  private List<String> extractValueList(String value) {

    // TODO: regex & groups
    List<String> result = new ArrayList<String>();

    if (StringUtils.isBlank(value)) {
      return result;
    }

    if (value.startsWith("[")) {
      try {
        result.addAll(GsonUtil.json2Obj(value, GsonUtil.stringListType));
      } catch (GsonUtilException ex) {
        if (StringUtils.isNotBlank(value)) {
          for (String s : value.split(",")) {
            result.add(s);
          }
        } else {
          result = null;
        }
      }
    } else if (value.startsWith("{")) {
      for (String s : value.substring(1, value.lastIndexOf("}")).split(",")) {
        result.add(s);
      }
    } else {
      for (String s : value.split(",")) {
        result.add(s);
      }
    }

    return result;

  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#datasetToDcat(java.lang.Object,
   * it.eng.idra.beans.odms.OdmsCatalogue)
   */
  @Override
  public DcatDataset datasetToDcat(Object dataset, OdmsCatalogue node) throws Exception {
    java.util.Map<String, JSONObject> cache = resolvedEntities;
    return it.eng.idra.utils.NgsiDcatReader.dataset((JSONObject)dataset, nodeId, id -> {
      if (cache.containsKey(id)) return cache.get(id);
      try { JSONObject value = getJsonDistribution(id); cache.put(id, value); return value; }
      catch (Exception e) { throw new IllegalStateException("Cannot resolve NGSI-LD entity " + id, e); }
    });
  }

  /**
   * Deserialize spatial.
   *
   * @param dataset the dataset
   * @param nodeId  the node id
   * @return the dct location
   */
  protected List<DctLocation> deserializeSpatial(JSONObject dataset, String nodeId) {
    List<DctLocation> locations = new ArrayList<>();
    try {
      Object obj = dataset.get("spatialCoverage");
      if (obj instanceof JSONArray) {
        JSONArray arr = (JSONArray) obj;
        for (int i = 0; i < arr.length(); i++) {
          JSONObject locObj = arr.getJSONObject(i);
          locations.add(new DctLocation(
              locObj.optString("uri"),
              locObj.optString("geographicalIdentifier"),
              locObj.optString("geographicalName"),
              locObj.optString("geometry"),
              nodeId,
              locObj.optString("bbox"),
              locObj.optString("centroid")));
        }
      } else if (obj instanceof JSONObject) {
        JSONObject locObj = (JSONObject) obj;
        locations.add(new DctLocation(
            locObj.optString("uri"),
            locObj.optString("geographicalIdentifier"),
            locObj.optString("geographicalName"),
            locObj.optString("geometry"),
            nodeId,
            locObj.optString("bbox"),
            locObj.optString("centroid")));
      }
    } catch (JSONException ignore) {
      logger.info("Spatial object not valid! - Skipped");
    }
    return locations;
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#getDataset(java.lang.String)
   */
  @Override
  public DcatDataset getDataset(String datasetId) throws Exception {
    return null;
  }

  /**
   * Gets the json datasets.
   *
   * @return the json datasets
   * 
   * @throws Exception exception
   */
  private JSONArray getJsonDatasets()
      throws Exception {
    Map<String, String> headers = new HashMap<String, String>();
    it.eng.idra.utils.NgsiDcatReader.contextHeaders(headers);
    headers.put("Accept", "application/json");
    RestClient client = new RestClientImpl();
    JSONArray result = new JSONArray();
    int offset = 0, pageSize = 1000;
    while (true) {
      HttpResponse response = client.sendGetRequest(node.getHost() + "/ngsi-ld/v1/entities?type=" + DATASET_TYPES + "&limit=" + pageSize + "&offset=" + offset, headers);
      int status = client.getStatus(response);
      String body = client.getHttpResponseBody(response);
      if (status != 200) throw new IllegalStateException("NGSI-LD dataset page failed: HTTP " + status);
      JSONArray page = new JSONArray(body);
      for (int i = 0; i < page.length(); i++) result.put(page.get(i));
      if (page.length() < pageSize) return result;
      offset += page.length();
    }
  }

  /**
   * Allows to subscribe to the CB.
   *
   * @throws Exception exception
   */
  public void subscribeToTheContextBroker() throws Exception {
    String endpoint=(subscriptionBaseUrl.endsWith("/")?subscriptionBaseUrl:subscriptionBaseUrl+"/")
        +"Idra/api/v1/client/notification/"+node.getId()+"/"+node.getApiKey()+"/push";
    it.eng.idra.utils.NgsiDcatSubscription.ensure(node.getHost(),node.getId(),endpoint);
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#getAllDatasets()
   */
  @Override
  public List<DcatDataset> getAllDatasets() throws Exception {

    ArrayList<DcatDataset> dcatDatasets = new ArrayList<DcatDataset>();

    List<String> datasetsNgsiId = new ArrayList<String>();
    resolvedEntities.clear();
    datasetsArray = getJsonDatasets();

    // System.out.println("NUM DATASET: " + datasetsArray.length());

    for (int i = 0; i < datasetsArray.length(); i++) {
      try {

        JSONObject dataset = datasetsArray.getJSONObject(i);
        logger.debug(dataset);
        DcatDataset dcatDataset = datasetToDcat(dataset, node);

        dcatDatasets.add(dcatDataset);
        logger.debug("aggiunto indice" + i);
        datasetsNgsiId.add(dcatDataset.getIdentifier().getValue());

        dataset = null;

      } catch (Exception e) {
        logger.info(e.getMessage() + " while deserializing "
            + "Dataset for Orion DCAT - " + i + " - SKIPPED");
        logger.info("There was an error: " + e.getMessage() + " while deserializing "
            + "Dataset for Orion DCAT - " + i + " - SKIPPED");
      }
    }
    // System.out.println("Num Id dei datasets recuperati: " +
    // datasetsNgsiId.size());
    logger.info("AUTOUPDATE of the node: " + node.getAutoUpdate());
    if (node.getAutoUpdate() == 1) {
      subscribeToTheContextBroker();
    }

    datasetsArray = null;

    return dcatDatasets;

  }

  /**
   * Gets the json distributions.
   *
   * @return the json distributions
   * 
   * @throws Exception exception
   */
  private JSONObject getJsonDistribution(String id)
      throws Exception {
    Map<String, String> headers = new HashMap<String, String>();
    it.eng.idra.utils.NgsiDcatReader.contextHeaders(headers);
    headers.put("Accept", "application/json");
    RestClient client = new RestClientImpl();
    String url = node.getHost() + "/ngsi-ld/v1/entities/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8);
    HttpResponse response = client.sendGetRequest(url, headers);
    int status = client.getStatus(response);
    String body = client.getHttpResponseBody(response);
    if (status == 404) return null;
    if (status != 200) throw new IllegalStateException("Cannot resolve entity: HTTP " + status);
    return new JSONObject(body);
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.connectors.IodmsConnector#getChangedDatasets(java.util.List,
   * java.lang.String)
   */
  @Override
  public OdmsSynchronizationResult getChangedDatasets(List<DcatDataset> oldDatasets,
      String startingDate) throws Exception {
    // return null;

    ArrayList<DcatDataset> newDatasets = (ArrayList<DcatDataset>) getAllDatasets();

    OdmsSynchronizationResult syncrhoResult = new OdmsSynchronizationResult();

    ImmutableSet<DcatDataset> newSets = ImmutableSet.copyOf(newDatasets);
    ImmutableSet<DcatDataset> oldSets = ImmutableSet.copyOf(oldDatasets);

    int deleted = 0;
    int added = 0;
    int changed = 0;

    /// Find added datasets
    // difference(current,present)
    SetView<DcatDataset> diff = Sets.difference(newSets, oldSets);
    logger.info("DIFF if added: " + diff.size());
    logger.info("New Packages: " + diff.size());
    for (DcatDataset d : diff) {
      syncrhoResult.addToAddedList(d);
      added++;
    }

    // Find removed datasets
    // difference(present,current)
    SetView<DcatDataset> diff1 = Sets.difference(oldSets, newSets);
    logger.info("DIFF if removed: " + diff.size());
    logger.info("Deleted Packages: " + diff1.size());
    for (DcatDataset d : diff1) {
      syncrhoResult.addToDeletedList(d);
      deleted++;
    }

    // Find updated datasets
    // intersection(present,current)
    SetView<DcatDataset> intersection = Sets.intersection(newSets, oldSets);
    logger.fatal("Changed Packages: " + intersection.size());

    GregorianCalendar oldDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    oldDate.setLenient(false);
    GregorianCalendar newDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    oldDate.setLenient(false);
    SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // SimpleDateFormat DCATDateF = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz
    // yyyy", Locale.US);

    int exception = 0;
    for (DcatDataset d : intersection) {
      try {
        int oldIndex = oldDatasets.indexOf(d);
        int newIndex = newDatasets.indexOf(d);
        oldDate.setTime(iso.parse(oldDatasets.get(oldIndex).getUpdateDate().getValue()));
        newDate.setTime(iso.parse(newDatasets.get(newIndex).getUpdateDate().getValue()));

        if (newDate.after(oldDate)) {
          syncrhoResult.addToChangedList(d);
          changed++;
        }
      } catch (Exception ex) {
        exception++;
        if (exception % 1000 == 0) {
          logger.error(ex.getMessage(), ex);
        }
      }
    }
    logger.info("Changed " + syncrhoResult.getChangedDatasets().size());
    logger.info("Added " + syncrhoResult.getAddedDatasets().size());
    logger.info("Deleted " + syncrhoResult.getDeletedDatasets().size());
    logger.info("Expected new dataset count: " + (node.getDatasetCount() - deleted + added));

    return syncrhoResult;

  }

  /**
   * Extract concept list.
   *
   * @param <T>         the generic type
   * @param propertyUri the property uri
   * @param concepts    the concepts
   * @param type        the type
   * @return the list
   */
  private <T extends SkosConcept> List<T> extractConceptList(String propertyUri,
      List<String> concepts, Class<T> type) {
    return it.eng.idra.utils.SkosConceptFactory.build(propertyUri, concepts, type, nodeId);
  }

}
