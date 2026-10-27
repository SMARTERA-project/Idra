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

package it.eng.idra.authentication;

import com.google.gson.Gson;
import it.eng.idra.authentication.filters.KeycloakAuthenticationFilter;
import it.eng.idra.authentication.fiware.configuration.IdmProperty;
import it.eng.idra.authentication.fiware.model.Token;
import it.eng.idra.authentication.keycloak.connector.KeycloakConnectorImpl;
import it.eng.idra.authentication.keycloak.model.KeycloakUser;
import it.eng.idra.utils.PropertyManager;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: Auto-generated Javadoc
/**
 * The Class KeycloakAuthenticationManager.
 */
public class KeycloakAuthenticationManager extends AuthenticationManager {

  /** The instance. */
  private static KeycloakAuthenticationManager instance;

  /** The connector. */
  private static KeycloakConnectorImpl connector;

  /** The Constant host. */
  private static final String host = resolveIdmHost();

  /** The Constant protocol. */
  private static final String protocol = PropertyManager.getProperty(IdmProperty.IDM_PROTOCOL);

  /** The Constant clientId. */
  private static final String clientId = PropertyManager.getProperty(IdmProperty.IDM_CLIENT_ID);

  /** The Constant clientSecret. */
  private static final String clientSecret = PropertyManager
      .getProperty(IdmProperty.IDM_CLIENT_SECRET);

  /** The Constant redirectUri. */
  private static final String redirectUri = PropertyManager
      .getProperty(IdmProperty.IDM_REDIRECT_URI);

  /** The Constant logoutCallback. */
  private static final String logoutCallback = PropertyManager
      .getProperty(IdmProperty.IDM_LOGOUT_CALLBACK);

  private static final Logger logger = LogManager.getLogger(KeycloakAuthenticationManager.class);

  private static KeycloakTokenVerifier verifier;

  /**
   * Instantiates a new keycloak authentication manager.
   */
  private KeycloakAuthenticationManager() {
    connector = new KeycloakConnectorImpl(protocol, host, -1, clientId, clientSecret, redirectUri);
    verifier = KeycloakTokenVerifier.fromConfiguration(protocol, host,
        PropertyManager.getProperty(IdmProperty.IDM_ISSUERS),
        PropertyManager.getProperty(IdmProperty.IDM_JWKS_URI));
  }

  /**
   * Gets the single instance of KeycloakAuthenticationManager.
   *
   * @return single instance of KeycloakAuthenticationManager
   */
  public static KeycloakAuthenticationManager getInstance() {

    if (instance == null) {
      try {
        instance = new KeycloakAuthenticationManager();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return instance;
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.authentication.AuthenticationManager
   * #login(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public Object login(String username, String password, String code) throws Exception {
    return getToken(null, code);
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.authentication.AuthenticationManager
   * #logout(javax.servlet.http.HttpServletRequest)
   */
  @Override
  public Response logout(HttpServletRequest request) throws Exception {

    System.out.println("Logging out...");

    HttpSession session = request.getSession();
    session.removeAttribute("loggedin");
    session.removeAttribute("refresh_token");
    session.removeAttribute("username");
    session.invalidate();

    return Response.temporaryRedirect(URI.create(logoutCallback)).build();

  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.authentication.AuthenticationManager
   * #getToken(java.lang.String, java.lang.String)
   */
  @Override
  public Token getToken(String username, String code) throws Exception {
    return connector.getToken(code);
  }

  /**
   * BFF token exchange: forwards the SPA's OAuth2 token request to Keycloak with
   * the confidential client credentials added server-side.
   *
   * @param formData the x-www-form-urlencoded body forwarded from the SPA
   * @return a 2-element array: [0] = HTTP status code, [1] = response body JSON
   * @throws Exception the exception
   */
  public String[] exchangeToken(String formData) throws Exception {
    return connector.exchangeToken(formData);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * it.eng.idra.authentication.AuthenticationManager#validateToken(java.lang.
   * Object)
   */
  @Override
  public Boolean validateToken(Object tokenObj) throws Exception {
    Token token = (Token) tokenObj;

    if (token == null || StringUtils.isBlank(token.getAccessToken())) {
      return false;
    }

    try {
      // JWTs are verified locally (realm JWKS signature, expiration, issuer); a JWT that fails
      // verification is rejected without falling back to /userinfo.
      if (KeycloakTokenVerifier.isCompactJws(token.getAccessToken())) {
        verifier.verify(token.getAccessToken());
        return true;
      }

      // Opaque tokens can only be validated by Keycloak itself.
      connector.getUserInfo(token.getAccessToken());
      return true;
    } catch (KeycloakTokenVerifier.InvalidTokenException e) {
      logger.warn("Rejected access token: {}", e.getMessage());
      return false;
    } catch (Exception e) {
      return false;
    }

  }

  /**
   * Validate admin role.
   *
   * @param user the user
   * @throws Exception the exception
   */
  public void validateAdminRole(KeycloakUser user) throws Exception {

    List<String> roles = new ArrayList<String>();
    
    if (CollectionUtils.isNotEmpty(user.getRealmAccess().getRoles())) {
      roles.addAll(user.getRealmAccess().getRoles().stream()
          .map(x -> x.toUpperCase()).collect(Collectors.toList()));
    }
    
    if (CollectionUtils.isNotEmpty(user.getRoles())) {
      roles.addAll(user.getRoles().stream()
          .map(x -> x.toUpperCase()).collect(Collectors.toList()));
    }
        
    if (roles != null && !roles.isEmpty() && roles
        .contains(PropertyManager.getProperty(IdmProperty.IDM_ADMIN_ROLE_NAME).toUpperCase())) {
      // OK
    } else {
      throw new Exception("The User has no Admin role");
    }

  }

  /**
   * Gets the user info.
   *
   * @param token the token
   * @return the user info
   * @throws Exception the exception
   */
  public KeycloakUser getUserInfo(String token) throws Exception {
    if (KeycloakTokenVerifier.isCompactJws(token)) {
      return toUserInfo(new Gson().fromJson(verifier.verify(token).toJson(),
          KeycloakJwtClaims.class));
    }
    return connector.getUserInfo(token);
  }

  /*
   * (non-Javadoc)
   * 
   * @see it.eng.idra.authentication.AuthenticationManager#getFilterClass()
   */
  @Override
  public Class<KeycloakAuthenticationFilter> getFilterClass() throws ClassNotFoundException {

    return KeycloakAuthenticationFilter.class;

  }

  private static String resolveIdmHost() {
    String configuredHost = PropertyManager.getProperty(IdmProperty.IDM_HOST);
    if (isResolvableHost(configuredHost)) {
      return configuredHost;
    }

    String uppercaseEnvHost = System.getenv("IDM_HOST");
    if (isResolvableHost(uppercaseEnvHost)) {
      return uppercaseEnvHost;
    }

    String defaultHost = PropertyManager.getDefaultProperty(IdmProperty.IDM_HOST);
    if (StringUtils.isNotBlank(defaultHost)) {
      return defaultHost;
    }

    return configuredHost;
  }

  private static boolean isResolvableHost(String idmHost) {
    String host = extractHost(idmHost);
    if (StringUtils.isBlank(host)) {
      return false;
    }
    try {
      InetAddress.getByName(host);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static String extractHost(String idmHost) {
    if (StringUtils.isBlank(idmHost)) {
      return null;
    }
    try {
      String urlValue = idmHost.contains("://") ? idmHost : "https://" + idmHost;
      URI uri = URI.create(urlValue);
      if (StringUtils.isNotBlank(uri.getHost())) {
        return uri.getHost();
      }
    } catch (Exception e) {
      // Ignore and fallback to manual parsing below.
    }

    String withoutPath = idmHost.split("/", 2)[0];
    return withoutPath.split(":", 2)[0];
  }

  private static KeycloakUser toUserInfo(KeycloakJwtClaims claims) {
    KeycloakUser user = new KeycloakUser();
    user.setSub(claims.sub);
    user.setPreferredUsername(claims.preferred_username);
    user.setEmail(claims.email);
    user.setName(claims.name);
    user.setGivenName(claims.given_name);
    user.setFamilyName(claims.family_name);
    user.setEmailVerified(Boolean.TRUE.equals(claims.email_verified));

    Set<String> roles = new HashSet<>();
    if (claims.roles != null) {
      roles.addAll(claims.roles);
    }
    if (claims.realm_access != null && claims.realm_access.roles != null) {
      roles.addAll(claims.realm_access.roles);

      KeycloakUser.RealmAccess realmAccess = user.new RealmAccess();
      realmAccess.setRoles(new HashSet<>(claims.realm_access.roles));
      user.setRealmAccess(realmAccess);
    }
    if (!roles.isEmpty()) {
      user.setRoles(roles);
    }
    return user;
  }

  private static class KeycloakJwtClaims {
    String sub;
    String preferred_username;
    String email;
    String name;
    String given_name;
    String family_name;
    Boolean email_verified;
    Long exp;
    Set<String> roles;
    RealmAccess realm_access;
  }

  private static class RealmAccess {
    Set<String> roles;
  }

}
