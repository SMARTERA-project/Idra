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

package it.eng.idra.exception;

import java.io.IOException;
import java.util.UUID;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String id = requestContext.getHeaderString(HEADER);
    if (id == null || id.isEmpty()) {
      id = UUID.randomUUID().toString();
    }
    requestContext.setProperty(MDC_KEY, id);
    // Write to both: SLF4J MDC (consumer code path) and log4j2 ThreadContext
    // (what the %X{} pattern actually reads from in this deployment).
    MDC.put(MDC_KEY, id);
    ThreadContext.put(MDC_KEY, id);
  }

  @Override
  public void filter(ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) throws IOException {
    Object id = requestContext.getProperty(MDC_KEY);
    if (id != null) {
      responseContext.getHeaders().putSingle(HEADER, id.toString());
    }
    MDC.remove(MDC_KEY);
    ThreadContext.remove(MDC_KEY);
  }
}
