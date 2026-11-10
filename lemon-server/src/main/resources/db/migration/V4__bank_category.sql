-- 模块下的分类（如：强化刷题、冲刺刷题、时政、冲刺背诵），题库可归入某个分类
CREATE TABLE `bank_category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `module_id`   BIGINT      NOT NULL COMMENT '所属模块',
  `name`        VARCHAR(32) NOT NULL COMMENT '名称',
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架（下架后其题库在用户端不显示）',
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库分类';

-- 题库所属分类，可为空（未分类）
ALTER TABLE `question_bank`
  ADD COLUMN `category_id` BIGINT NULL COMMENT '所属分类，NULL 为未分类' AFTER `module_id`,
  ADD KEY `idx_category` (`category_id`);
