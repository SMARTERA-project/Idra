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

package it.eng.idra.management;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.eng.idra.beans.IdraProperty;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.utils.PropertyManager;
import it.eng.idra.utils.restclient.RestClient;
import it.eng.idra.utils.restclient.RestClientImpl;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles automatic submission of federated catalogues to the MQA scoring
 * service. Triggered after a catalogue's DCAT-AP dump is (re)generated during
 * first federation and every successful synchronization.
 *
 * <p>By design this class never propagates exceptions: a failure (or an
 * unreachable MQA service) must never break federation or synchronization.
 */
public class MqaManager {

  /** The logger. */
  private static final Logger logger = LogManager.getLogger(MqaManager.class);

  private MqaManager() {
  }

  /**
   * Submits the catalogue's DCAT-AP dump to the MQA service for analysis.
   *
   * <p>MQA fetches the dump itself via the {@code file_url} pointing to Idra's
   * per-catalogue dump endpoint. On the first successful submission the returned
   * analysis id is stored on the catalogue and reused on later submissions so the
   * MQA service appends to the existing analysis history instead of creating
   * duplicates.
   *
   * @param node the federated catalogue
   */
  public static void submitCatalogue(OdmsCatalogue node) {
    try {
      if (!Boolean.parseBoolean(PropertyManager.getProperty(IdraProperty.MQA_ENABLED))) {
        return;
      }
      if (node == null || !node.isCacheable()) {
        return;
      }
      // MQA parsing requires at least one dataset in the dump.
      if (node.getDatasetCount() <= 0) {
        logger.info("MQA: skipping node " + node.getId() + " (no datasets to analyze)");
        return;
      }

      String mqaUrl = PropertyManager.getProperty(IdraProperty.MQA_URL);
      String idraBaseUrl = PropertyManager.getProperty(IdraProperty.MQA_IDRA_BASEURL);
      if (StringUtils.isBlank(mqaUrl) || StringUtils.isBlank(idraBaseUrl)) {
        logger.warn("MQA: idra.mqa.url or idra.mqa.idra.baseurl not configured, skipping node "
            + node.getId());
        return;
      }

      // forceDump=true so MQA always analyzes a freshly regenerated dump reflecting
      // the current cache state (the last completed synchronization), not a stale file.
      String fileUrl = StringUtils.removeEnd(idraBaseUrl.trim(), "/")
          + "/api/v1/client/dcat-ap/dump/" + node.getId() + "?forceDump=true";

      JsonObject body = new JsonObject();
      body.addProperty("file_url", fileUrl);
      // Pass the catalogue name explicitly: MQA's RDF-parsed title is unreliable for
      // catalogue dumps, so an explicit title keeps the analysis identifiable.
      if (StringUtils.isNotBlank(node.getName())) {
        body.addProperty("title", node.getName());
      }
      boolean reuseExisting = StringUtils.isNotBlank(node.getMqaAnalysisId());
      if (reuseExisting) {
        body.addProperty("id", node.getMqaAnalysisId());
      }

      Map<String, String> headers = new HashMap<String, String>();
      headers.put("Content-Type", "application/json");

      // Fresh client per call: getHttpResponseBody() shuts down the per-instance
      // connection manager, so it must not be shared.
      RestClient client = new RestClientImpl();
      // Trailing slash: MQA mounts the submit router at "/submit" with an inner
      // "/" route, so the concrete path is "/submit/" (avoids a 307 redirect).
      String api = StringUtils.removeEnd(mqaUrl.trim(), "/") + "/submit/";

      logger.info("MQA: submitting node " + node.getId() + " to " + api + " (file_url=" + fileUrl
          + ", reuseAnalysis=" + reuseExisting + ")");

      HttpResponse response = client.sendPostRequest(api, body.toString(),
          MediaType.APPLICATION_JSON_TYPE, headers);
      int status = (response != null) ? client.getStatus(response) : -1;

      if (status == 200) {
        String responseBody = client.getHttpResponseBody(response);
        // MQA returns an id when it creates a NEW analysis document: either the first
        // submission, or a self-healed one when the previously referenced analysis was
        // deleted. When re-analyzing an existing id it returns no id (appends history).
        String analysisId = extractId(responseBody);
        if (StringUtils.isNotBlank(analysisId) && !analysisId.equals(node.getMqaAnalysisId())) {
          node.setMqaAnalysisId(analysisId);
          try {
            // persist=true so the analysis id is written to the DB (not just the
            // in-memory federated-node list), enabling reuse on later submissions.
            OdmsManager.updateOdmsCatalogue(node, true);
          } catch (Exception e) {
            logger.error("MQA: failed to persist analysis id for node " + node.getId() + ": "
                + e.getMessage());
          }
        }
        logger.info("MQA: analysis submitted for node " + node.getId() + ", id="
            + (StringUtils.isNotBlank(node.getMqaAnalysisId()) ? node.getMqaAnalysisId()
                : "(none)") + ", reuse=" + reuseExisting);
      } else {
        logger.warn("MQA: submit failed for node " + node.getId() + ", status=" + status
            + (status == -1 ? " (MQA service not reachable)" : ""));
      }
    } catch (Exception e) {
      // Never let MQA problems break federation/synchronization.
      logger.error("MQA: unexpected error while submitting node "
          + (node != null ? node.getId() : "null") + ": " + e.getMessage(), e);
    }
  }

  /**
   * Extracts the {@code id} field from the MQA submit JSON response.
   *
   * @param responseBody the raw response body
   * @return the analysis id, or null if absent/unparseable
   */
  private static String extractId(String responseBody) {
    if (StringUtils.isBlank(responseBody)) {
      return null;
    }
    try {
      JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
      if (json.has("id") && !json.get("id").isJsonNull()) {
        return json.get("id").getAsString();
      }
    } catch (Exception e) {
      logger.warn("MQA: could not parse submit response: " + e.getMessage());
    }
    return null;
  }
}
