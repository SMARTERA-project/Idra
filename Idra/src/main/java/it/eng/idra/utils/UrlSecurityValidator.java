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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Shared SSRF guard for outbound HTTP requests built from user-supplied URLs.
 *
 * <p>A URL is considered safe only when its scheme is http/https and every
 * resolved IP address is publicly routable (not loopback, link-local,
 * site-local/RFC-1918, any-local, or multicast).
 */
public final class UrlSecurityValidator {

  private UrlSecurityValidator() {
  }

  /**
   * Returns {@code true} if the given URL is safe to fetch (public http/https
   * host), {@code false} otherwise (including malformed URLs or unresolvable
   * hosts).
   *
   * @param url the URL to check
   * @return whether the URL is a safe public target
   */
  public static boolean isSafePublicUrl(String url) {
    if (url == null || url.trim().isEmpty()) {
      return false;
    }
    final URI uri;
    try {
      uri = new URI(url.trim());
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null
        || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
      return false;
    }
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      return false;
    }
    try {
      // Validate every resolved address to reduce DNS-rebinding / multi-A-record bypass.
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        return false;
      }
      for (InetAddress addr : addresses) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
            || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
          return false;
        }
      }
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  /**
   * Convenience overload accepting a {@link URI}.
   *
   * @param uri the URI to check
   * @return whether the URI is a safe public target
   */
  public static boolean isSafePublicUrl(URI uri) {
    return uri != null && isSafePublicUrl(uri.toString());
  }

  /**
   * Throws {@link SecurityException} if the URL is not a safe public target.
   *
   * @param url the URL to validate
   */
  public static void assertSafePublicUrl(String url) {
    if (!isSafePublicUrl(url)) {
      throw new SecurityException("Refused to fetch non-public or invalid URL");
    }
  }
}
