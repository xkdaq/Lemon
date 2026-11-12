-- 新用户注册赠送「政治」「教育学」N 天（系统参数 app.register_gift_days，0 = 不赠送）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('app.register_gift_days', '365', '新用户注册赠送「政治」「教育学」的天数（0 = 不赠送）');

ALTER TABLE `module_access_log`
  MODIFY COLUMN `source` TINYINT NOT NULL COMMENT '1 激活码 2 后台开通 / 延长 3 后台取消 4 恢复老系统错题赠送 5 新用户注册赠送';

-- 2026-11-12 当天已注册的用户补赠送：注册时间 + 365 天（已有更晚的到期时间就不变）
INSERT INTO `module_access_log` (`user_id`, `module_id`, `source`, `before_expire`, `after_expire`, `remark`)
SELECT u.`id`, m.`id`, 5, a.`expire_at`, u.`created_at` + INTERVAL 365 DAY, '新用户注册赠送'
FROM `app_user` u
JOIN `module` m ON m.`name` IN ('政治', '教育学') AND m.`deleted` = 0
LEFT JOIN `user_module_access` a ON a.`user_id` = u.`id` AND a.`module_id` = m.`id`
WHERE u.`deleted` = 0 AND u.`created_at` >= '2026-11-12 00:00:00'
  AND (a.`expire_at` IS NULL OR a.`expire_at` < u.`created_at` + INTERVAL 365 DAY);

INSERT INTO `user_module_access` (`user_id`, `module_id`, `expire_at`)
SELECT u.`id`, m.`id`, u.`created_at` + INTERVAL 365 DAY
FROM `app_user` u
JOIN `module` m ON m.`name` IN ('政治', '教育学') AND m.`deleted` = 0
WHERE u.`deleted` = 0 AND u.`created_at` >= '2026-11-12 00:00:00'
ON DUPLICATE KEY UPDATE `expire_at` = GREATEST(`expire_at`, VALUES(`expire_at`));
