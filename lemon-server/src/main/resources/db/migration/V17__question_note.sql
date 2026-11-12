-- 题目笔记（私密，只有自己看得到）：每人每题一条，文字 + 最多 6 张图片
CREATE TABLE `question_note` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT        NOT NULL,
  `question_id` BIGINT        NOT NULL,
  `module_id`   BIGINT        NOT NULL COMMENT '冗余，按模块 / 题库筛选用',
  `bank_id`     BIGINT        NOT NULL,
  `chapter_id`  BIGINT        NOT NULL,
  `content`     TEXT          NULL COMMENT '纯文本，最多 2000 字',
  `images`      VARCHAR(2000) NULL COMMENT '图片 URL 的 JSON 数组',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_q` (`user_id`, `question_id`),
  KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目笔记（私密）';
