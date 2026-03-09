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

package it.eng.idra.management.security;

import it.eng.idra.beans.IdraProperty;
import it.eng.idra.beans.security.AppUser;
import it.eng.idra.utils.PropertyManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class RbacService {

  public static final String CTX_SUB = "idra.auth.sub";
  public static final String CTX_EMAIL = "idra.auth.email";
  public static final String CTX_USERNAME = "idra.auth.username";
  public static final String ROLE_IDRA_ADMIN = "IDRA_ADMIN";
  public static final String ROLE_IDRA_EDITOR = "IDRA_EDITOR";
  public static final String ROLE_IDRA_VIEWER = "IDRA_VIEWER";
  public static final String ROLE_IDRA_USER = "IDRA_USER";
  private static final Set<String> ALLOWED_IDRA_ROLES = new HashSet<>(
      Arrays.asList(ROLE_IDRA_ADMIN, ROLE_IDRA_EDITOR, ROLE_IDRA_VIEWER, ROLE_IDRA_USER));

  private static String normalizeExternalRole(String roleCode) {
    if (StringUtils.isBlank(roleCode)) {
      return roleCode;
    }
    return roleCode.trim().toUpperCase();
  }

  private static Set<String> parseCsvLower(String csv) {
    Set<String> out = new HashSet<>();
    if (StringUtils.isBlank(csv)) {
      return out;
    }
    Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toLowerCase)
        .forEach(out::add);
    return out;
  }

  private static Set<String> parseCsvExact(String csv) {
    Set<String> out = new HashSet<>();
    if (StringUtils.isBlank(csv)) {
      return out;
    }
    Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(out::add);
    return out;
  }

  public static AppUser ensureProvisionedUser(String sub, String username, String email) {
    SecurityPersistenceManager pm = new SecurityPersistenceManager();
    try {
      AppUser user = pm.upsertUserBySub(sub, username, email);

      if (user.getEnabled() != null && !user.getEnabled()) {
        throw new IllegalStateException("User is disabled");
      }

      String bootstrapEmailsCsv = PropertyManager.getProperty(IdraProperty.BOOTSTRAP_ADMIN_EMAILS);
      String bootstrapSubsCsv = PropertyManager.getProperty(IdraProperty.BOOTSTRAP_ADMIN_SUBS);

      Set<String> bootstrapEmails = parseCsvLower(bootstrapEmailsCsv);
      Set<String> bootstrapSubs = parseCsvExact(bootstrapSubsCsv);

      boolean isBootstrapAdmin = false;
      if (StringUtils.isNotBlank(email) && bootstrapEmails.contains(email.toLowerCase())) {
        isBootstrapAdmin = true;
      }
      if (!isBootstrapAdmin && bootstrapSubs.contains(sub)) {
        isBootstrapAdmin = true;
      }

      // If the user is in the bootstrap admin list, ensure IDRA_ADMIN is present.
      if (isBootstrapAdmin && !pm.userHasRoleCode(user.getId(), ROLE_IDRA_ADMIN)) {
        pm.addUserRoleByCode(user.getId(), ROLE_IDRA_ADMIN);
      }

      // Bootstrap roles only if user has no roles yet.
      if (!pm.userHasAnyRole(user.getId())) {
        String defaultRole = PropertyManager.getProperty(IdraProperty.BOOTSTRAP_DEFAULT_ROLE);
        if (StringUtils.isBlank(defaultRole)) {
          // Default to a non-administration role. Anonymous already accesses public APIs.
          defaultRole = ROLE_IDRA_USER;
        }

        // IDRA_ADMIN is the full-access role.
        String targetRole = isBootstrapAdmin ? ROLE_IDRA_ADMIN : defaultRole.trim();
        pm.addUserRoleByCode(user.getId(), targetRole);

        // Backward compatibility: if the configured default role does not exist yet in DB,
        // the INSERT does nothing. Ensure at least IDRA_VIEWER is assigned for authenticated users.
        if (!pm.userHasAnyRole(user.getId()) && !ROLE_IDRA_VIEWER.equals(targetRole)) {
          pm.addUserRoleByCode(user.getId(), ROLE_IDRA_VIEWER);
        }
      }

      return user;
    } finally {
      pm.close();
    }
  }

  public static boolean hasPermission(String sub, String permissionCode) {
    if (StringUtils.isBlank(permissionCode)) {
      return true;
    }
    SecurityPersistenceManager pm = new SecurityPersistenceManager();
    try {
      return pm.hasPermissionBySub(sub, permissionCode);
    } finally {
      pm.close();
    }
  }

  public static List<String> getPermissions(String sub) {
    SecurityPersistenceManager pm = new SecurityPersistenceManager();
    try {
      return pm.getPermissionsBySub(sub);
    } finally {
      pm.close();
    }
  }

  public static void syncUserRolesFromKeycloak(String sub, Set<String> keycloakRealmRoles) {
    if (StringUtils.isBlank(sub)) {
      return;
    }

    SecurityPersistenceManager pm = new SecurityPersistenceManager();
    try {
      AppUser user = pm.findUserBySub(sub);
      if (user == null) {
        return;
      }

      Set<String> dbRoleCodes = pm.listRoleCodes().stream()
          .filter(StringUtils::isNotBlank)
          .map(String::trim)
          .map(String::toUpperCase)
          .collect(Collectors.toSet());

      Set<String> wanted = new HashSet<>();
      if (keycloakRealmRoles != null) {
        keycloakRealmRoles.stream()
            .filter(StringUtils::isNotBlank)
            .map(RbacService::normalizeExternalRole)
            .filter(ALLOWED_IDRA_ROLES::contains)
            .filter(dbRoleCodes::contains)
            .forEach(wanted::add);
      }

      if (wanted.isEmpty()) {
        String fallback = PropertyManager.getProperty(IdraProperty.BOOTSTRAP_DEFAULT_ROLE);
        if (StringUtils.isBlank(fallback)) {
          fallback = ROLE_IDRA_USER;
        }
        fallback = fallback.trim().toUpperCase();
        if (dbRoleCodes.contains(fallback)) {
          wanted.add(fallback);
        } else if (dbRoleCodes.contains(ROLE_IDRA_USER)) {
          wanted.add(ROLE_IDRA_USER);
        } else if (dbRoleCodes.contains(ROLE_IDRA_VIEWER)) {
          wanted.add(ROLE_IDRA_VIEWER);
        }
      }

      Set<String> current = pm.getUserRoleCodes(user.getId()).stream()
          .filter(StringUtils::isNotBlank)
          .map(String::trim)
          .map(String::toUpperCase)
          .collect(Collectors.toSet());

      if (!current.equals(wanted)) {
        List<String> ordered = new ArrayList<>(wanted);
        Collections.sort(ordered);
        pm.replaceUserRolesByCodes(user.getId(), ordered);
      }
    } finally {
      pm.close();
    }
  }
}
