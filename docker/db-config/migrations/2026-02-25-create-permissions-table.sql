-- Creates per-user permissions for Administration APIs.
-- One row per user in `user`; each permission column enables/disables access to one admin API.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS `permissions` (
  `user_id` INT NOT NULL,

  -- /api/v1/administration/version
  `perm_get_version` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/catalogues
  `perm_get_odms_catalogues` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_add_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/catalogues/{nodeId}
  `perm_get_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_update_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_delete_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/catalogues/{nodeId}/activate|deactivate|synchronize
  `perm_activate_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_deactivate_odms_catalogue` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_start_odms_catalogue_synch` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/catalogues/{nodeId}/messages
  `perm_get_odms_catalogue_messages` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_delete_odms_catalogue_messages` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/catalogues/{nodeId}/messages/{messageId}
  `perm_get_odms_catalogue_message` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_delete_odms_catalogue_message` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/configuration
  `perm_get_settings` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_set_settings` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/prefixes
  `perm_add_prefix` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_prefixes` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/prefixes/{prefixId}
  `perm_get_prefix` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_delete_prefix` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_update_prefix` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/login|logout|verifyToken
  `perm_login_post` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_logout` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_verify_token` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/countries|cataloguesStatMinDate
  `perm_get_all_countries` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_min_date_nodes_stat` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/statistics/*
  `perm_get_node_statistics` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_search_statistics` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_keyword_statistics` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/logs
  `perm_get_logs` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/dcat-ap/dump*
  `perm_download_dataset_dump` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_dataset_dump` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_download_dataset_dump_by_node` TINYINT(1) NOT NULL DEFAULT 0,
  `perm_get_dataset_dump_by_node` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/datalets
  `perm_get_all_datalet` TINYINT(1) NOT NULL DEFAULT 0,

  -- /api/v1/administration/.../deleteDatalet/{dataletID}
  `perm_delete_datalet_from_distribution` TINYINT(1) NOT NULL DEFAULT 0,

  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_permissions_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ensure one permissions row exists for every current user (all permissions disabled by default).
INSERT INTO `permissions` (`user_id`)
SELECT u.`id`
FROM `user` u
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

COMMIT;
