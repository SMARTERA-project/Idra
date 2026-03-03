/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2026 Engineering Ingegneria Informatica S.p.A.
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

package it.eng.idra.authentication.filters;

import it.eng.idra.authentication.RequiresPermission;
import it.eng.idra.management.security.RbacService;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Context;

@RequiresPermission("")
@Provider
@Priority(2)
public class PermissionFilter implements ContainerRequestFilter {

  @Context
  private ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (resourceInfo == null || resourceInfo.getResourceMethod() == null) {
      return;
    }

    RequiresPermission rp = resourceInfo.getResourceMethod().getAnnotation(RequiresPermission.class);
    if (rp == null) {
      rp = resourceInfo.getResourceClass().getAnnotation(RequiresPermission.class);
    }
    if (rp == null) {
      return;
    }

    Object subObj = requestContext.getProperty(RbacService.CTX_SUB);
    String sub = subObj == null ? null : subObj.toString();
    if (sub == null || sub.trim().isEmpty()) {
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }

    String required = rp.value();
    if (required == null || required.trim().isEmpty()) {
      return;
    }

    if (!RbacService.hasPermission(sub, required.trim())) {
      requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
    }
  }
}

