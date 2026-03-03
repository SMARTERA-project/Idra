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
import it.eng.idra.management.security.SecurityPersistenceManager;
import it.eng.idra.beans.security.AppUser;
import it.eng.idra.utils.JwtUtil;
import it.eng.idra.utils.PropertyManager;
import it.eng.idra.authentication.fiware.configuration.IdmProperty;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Priority;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

// TODO: Auto-generated Javadoc
/**
 * The Class KeycloakAuthenticationFilter.
 */
@Secured
@Provider
@Priority(1)
public class KeycloakAuthenticationFilter implements ContainerRequestFilter {

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

        // Optional: if the user has the configured Keycloak admin realm role, bootstrap Idra ADMIN.
        // This does not replace DB RBAC; it only assigns ADMIN if missing.
        if (userinfo != null) {
          String adminRoleName = PropertyManager.getProperty(IdmProperty.IDM_ADMIN_ROLE_NAME);
          if (adminRoleName != null && !adminRoleName.isBlank()
              && userinfo.getRealmAccess() != null) {
            Set<String> roles = userinfo.getRealmAccess().getRoles();
            if (roles != null) {
              for (String r : roles) {
                if (r != null && r.equalsIgnoreCase(adminRoleName)) {
                  SecurityPersistenceManager pm = new SecurityPersistenceManager();
                  try {
                    AppUser u = pm.findUserBySub(sub);
                    if (u != null && !pm.userHasRoleCode(u.getId(), "ADMIN")) {
                      pm.addUserRoleByCode(u.getId(), "ADMIN");
                    }
                  } finally {
                    pm.close();
                  }
                  break;
                }
              }
            }
          }
        }
      } else {
        throw new Exception("Missing subject claim");
      }

    } catch (IllegalStateException e) {
      // Authenticated but not allowed to use Idra administration.
      requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
    } catch (RuntimeException e) {
      // DB / internal errors should not be masked as 401 (makes debugging impossible).
      e.printStackTrace();
      requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
    } catch (Exception e) {
      e.printStackTrace();
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }

  // Minimal JWT claims used by Idra RBAC bootstrap.
  private static class KeycloakJwtClaims {
    String sub;
    String preferred_username;
    String email;
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
