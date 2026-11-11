-- 1. 模块可设为免费（不需要激活）；「专业二」免费
ALTER TABLE `module`
  ADD COLUMN `free` TINYINT NOT NULL DEFAULT 0 COMMENT '1 免费（不需要激活） 0 收费（激活码激活）' AFTER `sort`;

UPDATE `module` SET `free` = 1 WHERE `name` = '专业二';

-- 2. 恢复过老系统错题的用户：赠送「政治」「教育学」到 2027-01-01 当天结束（已有更晚的到期时间就不变）
--    以后新恢复的用户由 LegacyRestoreService 在恢复时自动开通
INSERT INTO `module_access_log` (`user_id`, `module_id`, `source`, `before_expire`, `after_expire`, `remark`)
SELECT lu.`claimed_by`, m.`id`, 4, a.`expire_at`, '2027-01-01 23:59:59', '恢复老系统错题赠送'
FROM `legacy_user` lu
JOIN `module` m ON m.`name` IN ('政治', '教育学') AND m.`deleted` = 0
LEFT JOIN `user_module_access` a ON a.`user_id` = lu.`claimed_by` AND a.`module_id` = m.`id`
WHERE lu.`claimed_by` IS NOT NULL
  AND (a.`expire_at` IS NULL OR a.`expire_at` < '2027-01-01 23:59:59');

INSERT INTO `user_module_access` (`user_id`, `module_id`, `expire_at`)
SELECT DISTINCT lu.`claimed_by`, m.`id`, '2027-01-01 23:59:59'
FROM `legacy_user` lu
JOIN `module` m ON m.`name` IN ('政治', '教育学') AND m.`deleted` = 0
WHERE lu.`claimed_by` IS NOT NULL
ON DUPLICATE KEY UPDATE `expire_at` = GREATEST(`expire_at`, VALUES(`expire_at`));

ALTER TABLE `module_access_log`
  MODIFY COLUMN `source` TINYINT NOT NULL COMMENT '1 激活码 2 后台开通 / 延长 3 后台取消 4 恢复老系统错题赠送';
