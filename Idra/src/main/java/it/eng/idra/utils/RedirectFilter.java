/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.regex.Pattern;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;

// TODO: Auto-generated Javadoc
/**
 * The Class RedirectFilter.
 */
public class RedirectFilter implements ClientResponseFilter {

  private static final Pattern PRIVATE_IP = Pattern.compile(
      "^(127\\.|10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.|0\\.0\\.0\\.0|::1$|localhost$)",
      Pattern.CASE_INSENSITIVE);

  private boolean isSafeRedirectTarget(URI location) {
    if (location == null) return false;
    String scheme = location.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
    String host = location.getHost();
    if (host == null) return false;
    if (PRIVATE_IP.matcher(host).find()) return false;
    try {
      InetAddress addr = InetAddress.getByName(host);
      if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) return false;
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  /*
   * (non-Javadoc)
   *
   * @see javax.ws.rs.client.ClientResponseFilter#filter(javax.ws.rs.client.
   * ClientRequestContext, javax.ws.rs.client.ClientResponseContext)
   */
  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
      throws IOException {
    if (responseContext.getStatusInfo().getFamily() != Response.Status.Family.REDIRECTION) {
      return;
    }

    if (!isSafeRedirectTarget(responseContext.getLocation())) {
      return;
    }

    Response resp = requestContext.getClient().target(responseContext.getLocation()).request()
        .method(requestContext.getMethod());

    responseContext.setEntityStream((InputStream) resp.getEntity());
    responseContext.setStatusInfo(resp.getStatusInfo());
    responseContext.setStatus(resp.getStatus());

  }
}
