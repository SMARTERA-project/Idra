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
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Handles CORS OPTIONS preflight requests before any authentication filter runs.
 * Without this, Jersey may reject OPTIONS with 401 (no Authorization header)
 * or fail to pass the response through CorsResponseFilter, causing CORS errors.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CorsPreflightFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (!"OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
      return;
    }

    String origin = requestContext.getHeaderString("Origin");
    Response.ResponseBuilder builder = Response.ok()
        .header("Access-Control-Allow-Methods", "GET,POST,DELETE,PUT,OPTIONS")
        .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-XSRF-TOKEN");

    if (origin != null && isAllowedOrigin(origin)) {
      builder.header("Access-Control-Allow-Origin", origin)
             .header("Access-Control-Allow-Credentials", "true")
             .header("Vary", "Origin");
    }

    requestContext.abortWith(builder.build());
  }

  private boolean isAllowedOrigin(String origin) {
    String allowed = System.getProperty("idra.cors.allowed.origins", "");
    if (allowed.isBlank()) {
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
