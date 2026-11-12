-- 数据统计：每个用户每天是否活跃（当天有过请求），用于日活和留存；统计查询需要的时间索引
CREATE TABLE `user_active_day` (
  `user_id`     BIGINT NOT NULL,
  `active_date` DATE   NOT NULL,
  PRIMARY KEY (`user_id`, `active_date`),
  KEY `idx_date` (`active_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活跃日（每人每天一条）';

-- 历史数据补齐：注册当天、最后登录当天、有答题的每一天都算活跃
INSERT IGNORE INTO `user_active_day` (`user_id`, `active_date`)
SELECT `id`, DATE(`created_at`) FROM `app_user` WHERE `deleted` = 0;
INSERT IGNORE INTO `user_active_day` (`user_id`, `active_date`)
SELECT `id`, DATE(`last_login_at`) FROM `app_user` WHERE `deleted` = 0 AND `last_login_at` IS NOT NULL;
INSERT IGNORE INTO `user_active_day` (`user_id`, `active_date`)
SELECT DISTINCT `user_id`, DATE(`created_at`) FROM `answer_record`;

CREATE INDEX `idx_created` ON `answer_record` (`created_at`);
CREATE INDEX `idx_created` ON `app_user` (`created_at`);
CREATE INDEX `idx_created` ON `module_access_log` (`created_at`);
