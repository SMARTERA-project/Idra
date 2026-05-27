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

import java.util.Collections;
import java.util.Map;

public class AppException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;
  private final Map<String, String> details;

  public AppException(ErrorCode errorCode) {
    this(errorCode, errorCode.name(), null, Collections.emptyMap());
  }

  public AppException(ErrorCode errorCode, String technicalMessage) {
    this(errorCode, technicalMessage, null, Collections.emptyMap());
  }

  public AppException(ErrorCode errorCode, String technicalMessage, Throwable cause) {
    this(errorCode, technicalMessage, cause, Collections.emptyMap());
  }

  public AppException(ErrorCode errorCode, String technicalMessage, Throwable cause,
      Map<String, String> details) {
    super(technicalMessage, cause);
    this.errorCode = errorCode;
    this.details = (details == null)
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(details);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, String> getDetails() {
    return details;
  }
}
