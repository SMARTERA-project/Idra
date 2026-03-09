-- Normalize role codes to IDRA_* convention.
-- This migration is idempotent and supports both legacy and new installs.

START TRANSACTION;

-- Ensure canonical role codes exist.
INSERT INTO `role` (`code`, `description`) VALUES
  ('IDRA_ADMIN', 'Full access to Idra administration'),
  ('IDRA_EDITOR', 'Can modify federation resources within allowed domains'),
  ('IDRA_VIEWER', 'Read-only access to Idra administration'),
  ('IDRA_USER', 'Authenticated user with no administration access')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- Migrate users from legacy role names.
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT ur.`user_id`, rn.`id`
FROM `user_role` ur
JOIN `role` ro ON ur.`role_id` = ro.`id`
JOIN `role` rn ON rn.`code` = 'IDRA_ADMIN'
WHERE ro.`code` IN ('SUPER_ADMIN', 'ADMIN');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT ur.`user_id`, rn.`id`
FROM `user_role` ur
JOIN `role` ro ON ur.`role_id` = ro.`id`
JOIN `role` rn ON rn.`code` = 'IDRA_EDITOR'
WHERE ro.`code` IN ('EDITOR', 'MANAGER');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT ur.`user_id`, rn.`id`
FROM `user_role` ur
JOIN `role` ro ON ur.`role_id` = ro.`id`
JOIN `role` rn ON rn.`code` = 'IDRA_VIEWER'
WHERE ro.`code` = 'VIEWER';

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT ur.`user_id`, rn.`id`
FROM `user_role` ur
JOIN `role` ro ON ur.`role_id` = ro.`id`
JOIN `role` rn ON rn.`code` = 'IDRA_USER'
WHERE ro.`code` IN ('BASIC_USER', 'CITIZEN');

-- Remove role-permission mappings for legacy roles.
DELETE rp
FROM `role_permission` rp
JOIN `role` r ON rp.`role_id` = r.`id`
WHERE r.`code` IN ('SUPER_ADMIN', 'ADMIN', 'EDITOR', 'VIEWER', 'BASIC_USER', 'MANAGER', 'CITIZEN');

-- Remove user-role mappings for legacy roles.
DELETE ur
FROM `user_role` ur
JOIN `role` r ON ur.`role_id` = r.`id`
WHERE r.`code` IN ('SUPER_ADMIN', 'ADMIN', 'EDITOR', 'VIEWER', 'BASIC_USER', 'MANAGER', 'CITIZEN');

-- Drop legacy role codes.
DELETE FROM `role`
WHERE `code` IN ('SUPER_ADMIN', 'ADMIN', 'EDITOR', 'VIEWER', 'BASIC_USER', 'MANAGER', 'CITIZEN');

-- Canonical permission grants.
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'IDRA_ADMIN';

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

