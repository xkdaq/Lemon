-- 短信发送记录：每次实际调用短信服务都记一条（被限流挡掉的不记），管理后台「短信记录」查看
CREATE TABLE `sms_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `phone`      VARCHAR(20)  NOT NULL COMMENT '接收手机号',
  `scene`      TINYINT      NOT NULL COMMENT '1 登录 2 设置密码',
  `status`     TINYINT      NOT NULL COMMENT '1 已提交 0 提交失败 2 未实际发送（开发环境）',
  `request_id` VARCHAR(64)  NULL COMMENT '短信服务返回的请求 ID，可在 Spug 发送记录里查送达结果',
  `error`      VARCHAR(255) NULL COMMENT '失败原因',
  `ip`         VARCHAR(64)  NULL COMMENT '请求 IP',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录';
