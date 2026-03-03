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

package it.eng.idra.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.lang3.StringUtils;

public class JwtUtil {

  public static String decodeJwtPayloadJson(String jwt) {
    if (StringUtils.isBlank(jwt)) {
      return null;
    }
    String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    try {
      String payload = parts[1];
      // Base64 URL decoding may require padding depending on the encoder.
      int mod = payload.length() % 4;
      if (mod != 0) {
        payload = payload + "====".substring(mod);
      }
      byte[] decoded = Base64.getUrlDecoder().decode(payload);
      return new String(decoded, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      // Not a decodable JWT payload (or malformed token).
      return null;
    }
  }
}
