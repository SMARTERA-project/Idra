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

package it.eng.idra.authentication.filters;

import com.google.gson.Gson;
import it.eng.idra.authentication.KeycloakAuthenticationManager;
import it.eng.idra.authentication.Secured;
import it.eng.idra.authentication.fiware.model.Token;
import it.eng.idra.authentication.keycloak.model.KeycloakUser;
import it.eng.idra.management.security.RbacService;
import it.eng.idra.utils.JwtUtil;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Priority;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * The Class KeycloakAuthenticationFilter.
 */
@Secured
@Provider
@Priority(1)
public class KeycloakAuthenticationFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthenticationFilter.class);

  /*
   * (non-Javadoc)
   * 
   * @see
   * javax.ws.rs.container.ContainerRequestFilter#filter(javax.ws.rs.container.
   * ContainerRequestContext)
   */
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {

    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || authorizationHeader.equals("undefined")
        || !authorizationHeader.startsWith("Bearer ")) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }
    String token = authorizationHeader.substring("Bearer".length()).trim();
    try {

      // Validate token and retrieve userinfo (also used as fallback for JWT payload parsing).
      if (!KeycloakAuthenticationManager.getInstance().validateToken((Object) new Token(token))) {
        throw new Exception("Token not valid");
      }
      KeycloakUser userinfo = null;
      try {
        userinfo = KeycloakAuthenticationManager.getInstance().getUserInfo(token);
      } catch (Exception e) {
        // keep userinfo null; token is already validated, but provisioning may rely on claims fallback
      }

      // Extract key claims from the JWT payload after token validation.
      String payloadJson = JwtUtil.decodeJwtPayloadJson(token);
      KeycloakJwtClaims claims = null;
      if (payloadJson != null) {
        try {
          claims = new Gson().fromJson(payloadJson, KeycloakJwtClaims.class);
        } catch (Exception e) {
          claims = null;
        }
      }

      String sub = claims != null ? claims.sub : null;
      String username = claims != null ? claims.preferred_username : null;
      String email = claims != null ? claims.email : null;

      // Fallback to userinfo endpoint if JWT decoding fails (or required claims are missing).
      if ((sub == null || sub.isBlank()) && userinfo != null) {
        sub = userinfo.getSub();
      }
      if ((username == null || username.isBlank()) && userinfo != null) {
        username = userinfo.getPreferredUsername();
      }
      if ((email == null || email.isBlank()) && userinfo != null) {
        email = userinfo.getEmail();
      }

      if (sub != null && !sub.isBlank()) {
        requestContext.setProperty(RbacService.CTX_SUB, sub);
        requestContext.setProperty(RbacService.CTX_USERNAME, username);
        requestContext.setProperty(RbacService.CTX_EMAIL, email);

        // Ensure the user exists in Idra DB and has at least one role assigned.
        RbacService.ensureProvisionedUser(sub, username, email);

        // Sync DB roles from Keycloak roles on each authenticated call.
        // Prefer JWT access token claims (realm_access.roles) and enrich with userinfo roles when available.
        Set<String> keycloakRoles = new HashSet<>();
        if (claims != null) {
          if (claims.realm_access != null && claims.realm_access.roles != null) {
            keycloakRoles.addAll(claims.realm_access.roles);
          }
          if (claims.roles != null) {
            keycloakRoles.addAll(claims.roles);
          }
        }
        if (userinfo != null) {
          if (userinfo.getRealmAccess() != null && userinfo.getRealmAccess().getRoles() != null) {
            keycloakRoles.addAll(userinfo.getRealmAccess().getRoles());
          }
          if (userinfo.getRoles() != null) {
            keycloakRoles.addAll(userinfo.getRoles());
          }
        }
        RbacService.syncUserRolesFromKeycloak(sub, keycloakRoles);
      } else {
        throw new Exception("Missing subject claim");
      }

    } catch (IllegalStateException e) {
      // Authenticated but not allowed to use Idra administration.
      requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
    } catch (RuntimeException e) {
      // DB / internal errors should not be masked as 401 (makes debugging impossible).
      logger.error("Internal error in Keycloak auth filter", e);
      requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
    } catch (Exception e) {
      logger.warn("Keycloak auth filter rejecting request: {}", e.getMessage());
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }

  // Minimal JWT claims used by Idra RBAC bootstrap.
  private static class KeycloakJwtClaims {
    String sub;
    String preferred_username;
    String email;
    Set<String> roles;
    RealmAccess realm_access;
  }

  private static class RealmAccess {
    Set<String> roles;
  }

  // private void validateToken(String token) throws Exception {
  //
  // UserInfo user = idm.getUserInfo(token);
  // Set<Role> roles = user.getRoles();
  // if (roles != null && !roles.isEmpty()
  // && roles.contains(new
  // Role(PropertyManager.getProperty(IDMProperty.IDM_ADMIN_ROLE_NAME), null))) {
  // // OK
  // } else {
  // throw new Exception("The User has no Admin role");
  // }
  //
  // }
}
