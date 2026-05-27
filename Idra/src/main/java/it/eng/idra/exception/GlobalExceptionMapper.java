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

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import it.eng.idra.beans.ErrorResponse;
import it.eng.idra.utils.GsonUtilException;
import java.io.IOException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.jena.query.QueryParseException;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionMapper.class);

  // Toggle exposure of raw exception messages in the technicalMessage field.
  // Read once at classload — restart Idra to change.
  private static final boolean DEBUG_ERRORS =
      Boolean.parseBoolean(System.getProperty("idra.debug.errors", "false"));

  @Override
  public Response toResponse(Throwable t) {
    // Pass through pre-built JAX-RS responses (e.g. Keycloak filter 401 with explicit body)
    // — only for non-5xx so we don't accidentally swallow real server errors.
    if (t instanceof WebApplicationException) {
      WebApplicationException wae = (WebApplicationException) t;
      Response existing = wae.getResponse();
      if (existing != null && existing.getStatus() < 500 && existing.hasEntity()) {
        return existing;
      }
    }

    ErrorCode code = classify(t);
    // ExceptionMapper is singleton-scoped, so @Context ContainerRequestContext can't be
    // injected at construction time. The CorrelationIdFilter puts the id in SLF4J MDC
    // for the current thread — read it from there.
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

    if (code.httpStatus() >= 500) {
      logger.error("[errorCode={}] {}", code.name(), t.getMessage(), t);
    } else {
      logger.warn("[errorCode={}] {}", code.name(), t.getMessage());
    }

    ErrorResponse body = new ErrorResponse(
        String.valueOf(code.httpStatus()),
        DEBUG_ERRORS ? safeMessage(t) : null,
        code.name(),
        null,
        correlationId);

    return Response.status(code.httpStatus())
        .entity(body.toJson())
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private static ErrorCode classify(Throwable t) {
    if (t instanceof AppException) {
      return ((AppException) t).getErrorCode();
    }
    if (t instanceof JsonSyntaxException
        || t instanceof JsonParseException
        || t instanceof GsonUtilException) {
      return ErrorCode.ERR_JSON_PARSE;
    }
    if (t instanceof QueryParseException) {
      return ErrorCode.ERR_SPARQL_QUERY_INVALID;
    }
    if (t instanceof NotAuthorizedException || t instanceof SecurityException) {
      return ErrorCode.ERR_UNAUTHORIZED;
    }
    if (t instanceof ForbiddenException) {
      return ErrorCode.ERR_FORBIDDEN;
    }
    if (t instanceof NotFoundException) {
      return ErrorCode.ERR_NOT_FOUND;
    }
    if (t instanceof NumberFormatException || t instanceof IllegalArgumentException) {
      return ErrorCode.ERR_BAD_REQUEST;
    }
    if (t instanceof SolrServerException) {
      return ErrorCode.ERR_SEARCH_BACKEND;
    }
    if (t instanceof IOException) {
      return ErrorCode.ERR_FILE_IO;
    }
    return ErrorCode.ERR_INTERNAL;
  }

  // Strip filesystem paths and SQL fragments before exposing in technicalMessage.
  // Defence in depth — DEBUG_ERRORS should be off in prod anyway.
  private static String safeMessage(Throwable t) {
    String m = t.getMessage();
    if (m == null) {
      return t.getClass().getSimpleName();
    }
    return m
        .replaceAll("(?i)([a-z]:\\\\[^\\s]+|/[a-z0-9_./-]{4,})", "[path]")
        .replaceAll("(?i)\\b(insert|update|delete|select)\\s+[^\\n\\r]{20,}", "[sql]");
  }
}
