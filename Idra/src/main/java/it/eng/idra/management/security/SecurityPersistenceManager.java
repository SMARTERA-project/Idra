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

import it.eng.idra.beans.security.AppUser;
import it.eng.idra.beans.security.Permission;
import it.eng.idra.beans.security.Role;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

public class SecurityPersistenceManager {

  private static EntityManagerFactory emf;
  private EntityManager em;

  static {
    try {
      emf = Persistence.createEntityManagerFactory("org.hibernate.jpa.beans");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public SecurityPersistenceManager() {
    try {
      em = emf.createEntityManager();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void close() {
    try {
      if (em != null) {
        em.close();
      }
    } catch (Exception e) {
      // ignore
    }
  }

  public AppUser findUserBySub(String sub) {
    TypedQuery<AppUser> q = em.createQuery(
        "SELECT u FROM AppUser u WHERE u.keycloakSub = :sub", AppUser.class);
    q.setParameter("sub", sub);
    List<AppUser> res = q.getResultList();
    return res.isEmpty() ? null : res.get(0);
  }

  public AppUser upsertUserBySub(String sub, String username, String email) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      AppUser existing = findUserBySub(sub);
      if (existing == null) {
        AppUser u = new AppUser();
        u.setKeycloakSub(sub);
        u.setUsername(username);
        u.setEmail(email);
        u.setEnabled(true);
        u.setCreatedAt(new Date());
        u.setUpdatedAt(new Date());
        em.persist(u);
        tx.commit();
        return u;
      }
      boolean changed = false;
      if (username != null && (existing.getUsername() == null
          || !username.equals(existing.getUsername()))) {
        existing.setUsername(username);
        changed = true;
      }
      if (email != null && (existing.getEmail() == null || !email.equals(existing.getEmail()))) {
        existing.setEmail(email);
        changed = true;
      }
      if (existing.getEnabled() == null) {
        existing.setEnabled(true);
        changed = true;
      }
      if (changed) {
        existing.setUpdatedAt(new Date());
        em.merge(existing);
      }
      tx.commit();
      return existing;
    } catch (Exception e) {
      if (tx != null && tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  public List<String> getUserRoleCodes(int userId) {
    Query q = em.createNativeQuery(
        "SELECT r.code FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id = ?");
    q.setParameter(1, userId);
    @SuppressWarnings("unchecked")
    List<String> res = q.getResultList();
    return res;
  }

  public boolean userHasAnyRole(int userId) {
    Query q = em.createNativeQuery("SELECT COUNT(*) FROM user_role WHERE user_id = ?");
    q.setParameter(1, userId);
    Number n = (Number) q.getSingleResult();
    return n != null && n.longValue() > 0;
  }

  public boolean userHasRoleCode(int userId, String roleCode) {
    Query q = em.createNativeQuery(
        "SELECT COUNT(*) FROM user_role ur JOIN role r ON ur.role_id = r.id "
            + "WHERE ur.user_id = ? AND r.code = ?");
    q.setParameter(1, userId);
    q.setParameter(2, roleCode);
    Number n = (Number) q.getSingleResult();
    return n != null && n.longValue() > 0;
  }

  public void addUserRoleByCode(int userId, String roleCode) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      Query q = em.createNativeQuery(
          "INSERT IGNORE INTO user_role (user_id, role_id) "
              + "SELECT ?, r.id FROM role r WHERE r.code = ?");
      q.setParameter(1, userId);
      q.setParameter(2, roleCode);
      q.executeUpdate();
      tx.commit();
    } catch (Exception e) {
      if (tx != null && tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  public void replaceUserRolesByCodes(int userId, List<String> roleCodes) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      Query del = em.createNativeQuery("DELETE FROM user_role WHERE user_id = ?");
      del.setParameter(1, userId);
      del.executeUpdate();
      if (roleCodes != null) {
        for (String roleCode : roleCodes) {
          if (roleCode == null || roleCode.trim().isEmpty()) {
            continue;
          }
          Query ins = em.createNativeQuery(
              "INSERT IGNORE INTO user_role (user_id, role_id) "
                  + "SELECT ?, r.id FROM role r WHERE r.code = ?");
          ins.setParameter(1, userId);
          ins.setParameter(2, roleCode.trim());
          ins.executeUpdate();
        }
      }
      tx.commit();
    } catch (Exception e) {
      if (tx != null && tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  public void setUserEnabled(int userId, boolean enabled) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      Query q = em.createNativeQuery("UPDATE app_user SET enabled = ? WHERE id = ?");
      q.setParameter(1, enabled ? 1 : 0);
      q.setParameter(2, userId);
      q.executeUpdate();
      tx.commit();
    } catch (Exception e) {
      if (tx != null && tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  public boolean hasPermissionBySub(String sub, String permissionCode) {
    Query q = em.createNativeQuery(
        "SELECT COUNT(*) FROM app_user u "
            + "JOIN user_role ur ON ur.user_id = u.id "
            + "JOIN role_permission rp ON rp.role_id = ur.role_id "
            + "JOIN permission p ON p.id = rp.permission_id "
            + "WHERE u.keycloak_sub = ? AND u.enabled = 1 AND p.code = ?");
    q.setParameter(1, sub);
    q.setParameter(2, permissionCode);
    Number n = (Number) q.getSingleResult();
    return n != null && n.longValue() > 0;
  }

  public List<String> getPermissionsBySub(String sub) {
    Query q = em.createNativeQuery(
        "SELECT DISTINCT p.code FROM app_user u "
            + "JOIN user_role ur ON ur.user_id = u.id "
            + "JOIN role_permission rp ON rp.role_id = ur.role_id "
            + "JOIN permission p ON p.id = rp.permission_id "
            + "WHERE u.keycloak_sub = ? AND u.enabled = 1");
    q.setParameter(1, sub);
    @SuppressWarnings("unchecked")
    List<String> res = q.getResultList();
    return res;
  }

  public List<AppUser> listUsers() {
    TypedQuery<AppUser> q = em.createQuery("SELECT u FROM AppUser u ORDER BY u.id", AppUser.class);
    return q.getResultList();
  }

  public List<Role> listRoles() {
    TypedQuery<Role> q = em.createQuery("SELECT r FROM Role r ORDER BY r.code", Role.class);
    return q.getResultList();
  }

  public Set<String> listRoleCodes() {
    Query q = em.createNativeQuery("SELECT code FROM role");
    @SuppressWarnings("unchecked")
    List<String> res = q.getResultList();
    return new HashSet<>(res);
  }

  public List<Permission> listPermissions() {
    TypedQuery<Permission> q = em.createQuery("SELECT p FROM Permission p ORDER BY p.code",
        Permission.class);
    return q.getResultList();
  }
}
