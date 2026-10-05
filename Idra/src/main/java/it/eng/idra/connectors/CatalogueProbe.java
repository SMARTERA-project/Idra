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

import it.eng.idra.beans.odms.OdmsCatalogueState;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lightweight availability check for connectors whose dataset count requires downloading
 * the whole catalogue: a single HEAD (or minimal GET) request to the catalogue host.
 *
 * <p>Not an SSRF guard: it is used only for catalogues configured by an administrator,
 * which may legitimately live on the internal network.
 */
final class CatalogueProbe {

  private static final int TIMEOUT_MS = 10000;

  private CatalogueProbe() {
  }

  /**
   * Returns ONLINE when the host answers with a status below 500.
   *
   * @param host the catalogue host URL
   * @return the catalogue state
   * @throws Exception when the host cannot be reached (caller marks the node OFFLINE)
   */
  static OdmsCatalogueState checkHost(String host) throws Exception {
    int code = request(host, "HEAD");
    if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
      code = request(host, "GET");
    }
    return code < 500 ? OdmsCatalogueState.ONLINE : OdmsCatalogueState.OFFLINE;
  }

  private static int request(String host, String method) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(host.trim()).openConnection();
    try {
      conn.setRequestMethod(method);
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);
      conn.setInstanceFollowRedirects(true);
      if ("GET".equals(method)) {
        conn.setRequestProperty("Range", "bytes=0-0");
      }
      return conn.getResponseCode();
    } finally {
      conn.disconnect();
    }
  }
}
