-- =============================================================
-- 模块激活：题库按模块收费，激活码激活某个模块后，模块下所有题库都能刷
-- 没激活的模块可以浏览目录，每个章节免费试做前 N 题（系统参数 app.trial_questions）
-- =============================================================

CREATE TABLE `activation_code` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `code`          VARCHAR(32)  NOT NULL COMMENT '激活码（大写，不含分隔符）',
  `module_id`     BIGINT       NOT NULL COMMENT '激活的模块',
  `batch_name`    VARCHAR(64)  NULL COMMENT '批次 / 渠道备注，如「闲鱼 11 月」',
  `validity_type` TINYINT      NOT NULL COMMENT '1 激活后有效 N 天 2 到固定日期为止',
  `valid_days`    INT          NULL COMMENT 'validity_type=1：激活后有效天数',
  `expire_at`     DATETIME     NULL COMMENT 'validity_type=2：到期时间',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未使用 1 已使用 2 已作废',
  `used_by`       BIGINT       NULL COMMENT '使用者 app_user.id',
  `used_at`       DATETIME     NULL COMMENT '使用时间',
  `access_until`  DATETIME     NULL COMMENT '使用后该模块的到期时间（含顺延）',
  `created_by`    BIGINT       NULL COMMENT '生成的管理员',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_module_status` (`module_id`, `status`),
  KEY `idx_used_by` (`used_by`),
  KEY `idx_batch` (`batch_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激活码';

CREATE TABLE `user_module_access` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL,
  `module_id`  BIGINT   NOT NULL,
  `expire_at`  DATETIME NOT NULL COMMENT '到期时间，之后恢复为未激活',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_module` (`user_id`, `module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户的模块权限';

CREATE TABLE `module_access_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `module_id`     BIGINT       NOT NULL,
  `source`        TINYINT      NOT NULL COMMENT '1 激活码 2 后台开通 / 延长 3 后台取消',
  `code_id`       BIGINT       NULL COMMENT 'source=1：使用的激活码',
  `admin_id`      BIGINT       NULL COMMENT 'source=2/3：操作的管理员',
  `before_expire` DATETIME     NULL COMMENT '变更前到期时间',
  `after_expire`  DATETIME     NULL COMMENT '变更后到期时间',
  `remark`        VARCHAR(255) NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_code` (`code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块权限变更记录';

ALTER TABLE `practice_session`
  ADD COLUMN `trial` TINYINT NOT NULL DEFAULT 0 COMMENT '1 = 未激活时的试做会话（只有章节前 N 题）' AFTER `section`;

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('app.trial_questions', '5', '未激活的模块，每个章节免费试做的题数（0 = 不能试做）');
