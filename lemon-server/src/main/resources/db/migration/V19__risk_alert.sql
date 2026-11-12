-- 风险告警（防偷题）：同一账号短时间内看过的题目数、开练习次数超过阈值时记录，后台「风险告警」处理
CREATE TABLE `risk_alert` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `alert_type`  VARCHAR(32)  NOT NULL COMMENT 'hour_questions 1 小时看题过多 / day_questions 1 天看题过多 / hour_sessions 1 小时开练习过多',
  `alert_date`  DATE         NOT NULL COMMENT '同一账号同一类型每天只记一条，数值随后更新为最大值',
  `metric`      INT          NOT NULL COMMENT '触发时的数值（不同题目数 / 开练习次数）',
  `threshold`   INT          NOT NULL COMMENT '当时的阈值',
  `ip`          VARCHAR(64)  NULL COMMENT '最近一次请求的 IP',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已处理',
  `handled_by`  BIGINT       NULL COMMENT '处理的管理员',
  `handled_at`  DATETIME     NULL,
  `remark`      VARCHAR(255) NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_date` (`user_id`, `alert_type`, `alert_date`),
  KEY `idx_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险告警';

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('risk.hour_questions', '600', '风险告警：同一账号 1 小时内看过的不同题目数超过此值告警（0 = 不检查）'),
  ('risk.day_questions', '2000', '风险告警：同一账号 1 天内看过的不同题目数超过此值告警（0 = 不检查）'),
  ('risk.hour_sessions', '60', '风险告警：同一账号 1 小时内开始练习的次数超过此值告警（0 = 不检查）');
