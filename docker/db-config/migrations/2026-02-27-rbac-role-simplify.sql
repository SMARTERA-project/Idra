-- Simplify RBAC roles:
-- - Remove SUPER_ADMIN (merge into ADMIN)
-- - Add BASIC_USER (no administration access)
-- - Make VIEWER read-only for administration APIs
-- Run this after 2026-02-26-rbac-multiuser.sql on existing databases.

START TRANSACTION;

-- Ensure base role exists (authenticated but no administration access).
INSERT INTO `role` (`code`, `description`) VALUES
  ('BASIC_USER', 'Authenticated user with no administration access')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- ADMIN is the single full-access admin role now.
UPDATE `role`
SET `description` = 'Full access to Idra administration'
WHERE `code` = 'ADMIN';

-- If SUPER_ADMIN exists, migrate its users to ADMIN.
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT ur.`user_id`, rAdmin.`id`
FROM `user_role` ur
JOIN `role` rSuper ON ur.`role_id` = rSuper.`id` AND rSuper.`code` = 'SUPER_ADMIN'
JOIN `role` rAdmin ON rAdmin.`code` = 'ADMIN';

-- Remove SUPER_ADMIN bindings (role will be dropped).
DELETE ur
FROM `user_role` ur
JOIN `role` rSuper ON ur.`role_id` = rSuper.`id`
WHERE rSuper.`code` = 'SUPER_ADMIN';

DELETE rp
FROM `role_permission` rp
JOIN `role` rSuper ON rp.`role_id` = rSuper.`id`
WHERE rSuper.`code` = 'SUPER_ADMIN';

DELETE FROM `role` WHERE `code` = 'SUPER_ADMIN';

-- Grant ADMIN all permissions (including admin.users.manage).
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'ADMIN';

-- VIEWER becomes read-only on administration.
DELETE rp
FROM `role_permission` rp
JOIN `role` r ON rp.`role_id` = r.`id`
WHERE r.`code` = 'VIEWER';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'VIEWER'
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

