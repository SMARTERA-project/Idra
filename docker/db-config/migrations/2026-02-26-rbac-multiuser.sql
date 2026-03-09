-- Adds multi-user RBAC tables (Keycloak-only authentication/registration).
-- Run this once on existing databases before/after upgrading application code.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `keycloak_sub` VARCHAR(255) NOT NULL,
  `username` VARCHAR(255) DEFAULT NULL,
  `email` VARCHAR(255) DEFAULT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_keycloak_sub` (`keycloak_sub`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permission` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` INT NOT NULL,
  `role_id` INT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_user_role_role_id` (`role_id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_permission` (
  `role_id` INT NOT NULL,
  `permission_id` INT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`),
  KEY `idx_role_permission_permission_id` (`permission_id`),
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed roles.
INSERT INTO `role` (`code`, `description`) VALUES
  ('IDRA_ADMIN', 'Full access to Idra administration'),
  ('IDRA_EDITOR', 'Can modify federation resources within allowed domains'),
  ('IDRA_VIEWER', 'Read-only access to Idra administration'),
  ('IDRA_USER', 'Authenticated user with no administration access')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- Seed permissions (domain-oriented, not per-endpoint).
INSERT INTO `permission` (`code`, `description`) VALUES
  ('admin.catalogue.read', 'Read federated catalogues (administration)'),
  ('admin.catalogue.write', 'Create/update federated catalogues (administration)'),
  ('admin.catalogue.delete', 'Delete federated catalogues (administration)'),
  ('admin.catalogue.activate', 'Activate/deactivate/synchronize catalogues (administration)'),
  ('admin.messages.read', 'Read catalogue messages (administration)'),
  ('admin.messages.delete', 'Delete catalogue messages (administration)'),
  ('admin.settings.read', 'Read platform configuration (administration)'),
  ('admin.settings.write', 'Update platform configuration (administration)'),
  ('admin.prefix.read', 'Read RDF prefixes (administration)'),
  ('admin.prefix.write', 'Create/update/delete RDF prefixes (administration)'),
  ('admin.stats.read', 'Read statistics (administration)'),
  ('admin.logs.read', 'Read logs (administration)'),
  ('admin.dump.read', 'Read/download DCAT-AP dumps (administration)'),
  ('admin.datalet.read', 'Read datalets (administration)'),
  ('admin.datalet.delete', 'Delete datalets (administration)'),
  ('admin.users.manage', 'Manage Idra users/roles/permissions (administration)')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- Grant IDRA_ADMIN all permissions.
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'IDRA_ADMIN';

-- Grant IDRA_EDITOR a restricted set (catalogue/prefix/dump/datalet read/write where reasonable).
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'IDRA_EDITOR'
  AND p.`code` IN (
    'admin.catalogue.read','admin.catalogue.write','admin.catalogue.activate',
    'admin.prefix.read','admin.prefix.write',
    'admin.dump.read',
    'admin.datalet.read'
  );

-- IDRA_VIEWER read-only permissions on administration.
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'IDRA_VIEWER'
  AND p.`code` IN (
    'admin.catalogue.read',
    'admin.messages.read',
    'admin.settings.read',
    'admin.prefix.read',
    'admin.stats.read',
    'admin.logs.read',
    'admin.dump.read',
    'admin.datalet.read'
  );

COMMIT;
