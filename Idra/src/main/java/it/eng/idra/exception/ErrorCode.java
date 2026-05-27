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

// Stable contract: error code names are part of the public API consumed by
// frontends (IdraPortal-ngx maps ERR_FOO_BAR -> ERR.FOO.BAR i18n key).
// Never rename or remove an existing value: only add new ones.
public enum ErrorCode {

  // Generic
  ERR_INTERNAL(500, "ERR.GENERIC.INTERNAL"),
  ERR_BAD_REQUEST(400, "ERR.GENERIC.BAD_REQUEST"),
  ERR_NOT_FOUND(404, "ERR.GENERIC.NOT_FOUND"),
  ERR_CONFLICT(409, "ERR.GENERIC.CONFLICT"),
  ERR_UPSTREAM_UNAVAILABLE(503, "ERR.GENERIC.UPSTREAM_UNAVAILABLE"),
  ERR_TIMEOUT(504, "ERR.GENERIC.TIMEOUT"),

  // Auth
  ERR_UNAUTHORIZED(401, "ERR.AUTH.UNAUTHORIZED"),
  ERR_FORBIDDEN(403, "ERR.AUTH.FORBIDDEN"),
  ERR_AUTH_INVALID_PASSWORD(401, "ERR.AUTH.INVALID_PASSWORD"),
  ERR_AUTH_EXPIRED(401, "ERR.AUTH.EXPIRED"),

  // Validation
  ERR_VALIDATION_FAILED(400, "ERR.VALIDATION.FAILED"),
  ERR_VALIDATION_MISSING_FIELD(400, "ERR.VALIDATION.MISSING_FIELD"),

  // Dataset
  ERR_DATASET_NOT_FOUND(404, "ERR.DATASET.NOT_FOUND"),
  ERR_DATASET_INVALID(400, "ERR.DATASET.INVALID"),
  ERR_DATASET_DUPLICATE(409, "ERR.DATASET.DUPLICATE"),
  ERR_DISTRIBUTION_NOT_FOUND(404, "ERR.DISTRIBUTION.NOT_FOUND"),

  // Catalogue (ODMS)
  ERR_CATALOGUE_NOT_FOUND(404, "ERR.CATALOGUE.NOT_FOUND"),
  ERR_CATALOGUE_DUPLICATE(409, "ERR.CATALOGUE.DUPLICATE"),
  ERR_CATALOGUE_OFFLINE(503, "ERR.CATALOGUE.OFFLINE"),
  ERR_CATALOGUE_FORBIDDEN(403, "ERR.CATALOGUE.FORBIDDEN"),
  ERR_CATALOGUE_SSL(502, "ERR.CATALOGUE.SSL"),
  ERR_CATALOGUE_STATE_CHANGE(409, "ERR.CATALOGUE.STATE_CHANGE"),
  ERR_CATALOGUE_MANAGER(500, "ERR.CATALOGUE.MANAGER"),

  // EuroVoc
  ERR_EUROVOC_NOT_FOUND(404, "ERR.EUROVOC.NOT_FOUND"),

  // Persistence / DB
  ERR_PERSISTENCE(500, "ERR.PERSISTENCE.GENERIC"),
  ERR_PERSISTENCE_CONSTRAINT(409, "ERR.PERSISTENCE.CONSTRAINT"),

  // Search / Solr
  ERR_SEARCH_QUERY_INVALID(400, "ERR.SEARCH.QUERY_INVALID"),
  ERR_SEARCH_BACKEND(502, "ERR.SEARCH.BACKEND"),

  // SPARQL
  ERR_SPARQL_QUERY_INVALID(400, "ERR.SPARQL.QUERY_INVALID"),

  // Dump / RDF
  ERR_DUMP_NOT_FOUND(404, "ERR.DUMP.NOT_FOUND"),
  ERR_DUMP_GENERATION(500, "ERR.DUMP.GENERATION"),

  // File I/O
  ERR_FILE_NOT_FOUND(404, "ERR.FILE.NOT_FOUND"),
  ERR_FILE_IO(500, "ERR.FILE.IO"),

  // Parse
  ERR_JSON_PARSE(400, "ERR.PARSE.JSON"),
  ERR_RDF_PARSE(400, "ERR.PARSE.RDF");

  private final int httpStatus;
  private final String userMessageKey;

  ErrorCode(int httpStatus, String userMessageKey) {
    this.httpStatus = httpStatus;
    this.userMessageKey = userMessageKey;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String userMessageKey() {
    return userMessageKey;
  }
}
