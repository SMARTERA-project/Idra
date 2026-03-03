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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class RbacService {

  public static final String CTX_SUB = "idra.auth.sub";
  public static final String CTX_EMAIL = "idra.auth.email";
  public static final String CTX_USERNAME = "idra.auth.username";

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

      // If the user is in the bootstrap admin list, ensure ADMIN is present (do not remove other roles).
      if (isBootstrapAdmin && !pm.userHasRoleCode(user.getId(), "ADMIN")) {
        pm.addUserRoleByCode(user.getId(), "ADMIN");
      }

      // Bootstrap roles only if user has no roles yet.
      if (!pm.userHasAnyRole(user.getId())) {
        String defaultRole = PropertyManager.getProperty(IdraProperty.BOOTSTRAP_DEFAULT_ROLE);
        if (StringUtils.isBlank(defaultRole)) {
          // Default to a non-administration role. Anonymous already accesses public APIs.
          defaultRole = "BASIC_USER";
        }

        // "ADMIN" is the full-access role (SUPER_ADMIN removed).
        String targetRole = isBootstrapAdmin ? "ADMIN" : defaultRole.trim();
        pm.addUserRoleByCode(user.getId(), targetRole);

        // Backward compatibility: if the configured default role does not exist yet in DB,
        // the INSERT does nothing. Ensure at least VIEWER is assigned for authenticated users.
        if (!pm.userHasAnyRole(user.getId()) && !"VIEWER".equals(targetRole)) {
          pm.addUserRoleByCode(user.getId(), "VIEWER");
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
}
