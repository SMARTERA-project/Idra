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

package it.eng.idra.api;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

/**
 * The Class CorsResponseFilter.
 */
@Provider
public class CorsResponseFilter implements ContainerResponseFilter {

  /**
   * filter.
   *
   * @param requestContext  the request context
   * @param responseContext the response context
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void filter(ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) throws IOException {

    MultivaluedMap<String, Object> headers = responseContext.getHeaders();

    String origin = requestContext.getHeaderString("Origin");
    if (origin != null && isAllowedOrigin(origin)) {
      // Reflect specific origin so credentials (cookies, auth) are accepted by the browser.
      // "*" + "Access-Control-Allow-Credentials: true" is invalid and browsers reject it.
      headers.putSingle("Access-Control-Allow-Origin", origin);
      headers.putSingle("Access-Control-Allow-Credentials", "true");
      headers.putSingle("Vary", "Origin");
    }
    // If origin is not in the allowlist, no Access-Control-Allow-Origin header is set
    // and the browser blocks the response — correct CORS security behavior.

    headers.putSingle("Access-Control-Allow-Methods", "GET,POST,DELETE,PUT,OPTIONS");
    headers.putSingle("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-XSRF-TOKEN");
  }

  private boolean isAllowedOrigin(String origin) {
    String allowed = System.getProperty("idra.cors.allowed.origins", "");
    if (allowed == null || allowed.isBlank()) {
      return false;
    }
    for (String a : allowed.split(",")) {
      if (a.trim().equalsIgnoreCase(origin)) {
        return true;
      }
    }
    return false;
  }

}
