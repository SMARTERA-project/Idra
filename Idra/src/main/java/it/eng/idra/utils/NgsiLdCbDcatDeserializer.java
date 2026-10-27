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

package it.eng.idra.utils;

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
import it.eng.idra.connectors.NgsiLdCbDcatConnector;
import it.eng.idra.management.FederationCore;
import it.eng.idra.utils.restclient.RestClient;
import it.eng.idra.utils.restclient.RestClientImpl;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.json.JSONObject;

// TODO: Auto-generated Javadoc
/**
 * The Class OrionDcatDeserializer.
 */
public class NgsiLdCbDcatDeserializer {
  private static Logger logger = LogManager.getLogger(NgsiLdCbDcatConnector.class);

  // private static Logger logger =
  // LogManager.getLogger(OrionDcatConnector.class);

  /**
   * Gets Dataset From Json.
   *
   * @return the DcatDataset
   * 
   * @throws Exception exception
   */
  public static DcatDataset getDatasetFromJson(String returnedJson,
      OdmsCatalogue node) throws Exception {

    Object payload = new org.json.JSONTokener(returnedJson).nextValue();
    JSONObject dataset = payload instanceof JSONArray ? ((JSONArray)payload).getJSONObject(0) : (JSONObject)payload;
    DcatDataset dcatDataset = datasetToDcat(dataset, node);
    logger.info("fine dataset to dcat");
    return dcatDataset;
  }

  /**
   * Gets DcatDataset From an Object dataset.
   *
   * @return the DcatDataset
   * 
   * @throws Exception exception
   */
  public static DcatDataset datasetToDcat(Object dataset, OdmsCatalogue node) throws Exception {
    return NgsiDcatReader.dataset((JSONObject)dataset, String.valueOf(node.getId()), id -> {
      try { return getJsonDistribution(id, node); }
      catch (Exception e) { throw new IllegalStateException("Cannot resolve NGSI-LD entity " + id, e); }
    });
  }

  /**
   * Gets the json distributions.
   *
   * @return the json distributions
   * 
   * @throws Exception exception
   */
  private static JSONObject getJsonDistribution(String id, OdmsCatalogue node)
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

  /**
   * Gets a dcat distribution.
   *
   * @return the dcat distribution
   * 
   * @throws Exception exception
   */
  public static DcatDistribution distributionToDcat(Object distribution,
      OdmsCatalogue node) throws Exception {
    return NgsiDcatReader.distribution((JSONObject)distribution, String.valueOf(node.getId()), id -> {
      try { return getJsonDistribution(id, node); }
      catch (Exception e) { throw new IllegalStateException("Cannot resolve NGSI-LD entity " + id, e); }
    });
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
  public static <T extends SkosConcept> List<T> extractConceptList(String propertyUri,
      List<String> concepts, Class<T> type, OdmsCatalogue node) {
    List<T> result = new ArrayList<T>();

    for (String label : concepts) {
      try {
        result.add(type.getDeclaredConstructor(SkosConcept.class).newInstance(new SkosConcept(
            propertyUri, "", Arrays.asList(new SkosPrefLabel("", FederationCore.getEnglishDcatTheme(label),
                String.valueOf(node.getId()))),
            String.valueOf(node.getId()))));
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
          | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        // TODO Auto-generated catch block
        logger.error(e.getMessage(), e);
      }
    }

    return result;
  }

}
