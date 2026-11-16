-- 背诵模块：独立于题库，不改动 question 等已有表。免费开放（不走模块激活）。
-- 一份背诵资料（如 27肖八选择题）= 若干套，每套若干题，每题一组背诵要点。

CREATE TABLE `recite_book` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(64)   NOT NULL COMMENT '名称，如 27肖八选择题',
  `intro`         VARCHAR(255)  NULL COMMENT '简介',
  `set_list`      VARCHAR(2000) NULL COMMENT '套列表 JSON：[{"no":1,"name":"第一套"}]',
  `sort`          INT           NOT NULL DEFAULT 0,
  `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  `item_count`    INT           NOT NULL DEFAULT 0 COMMENT '题数（冗余）',
  `link_bank_id`  BIGINT        NULL COMMENT '导入时用来对照的题库（只读），用于“去刷这套”',
  `linked_count`  INT           NOT NULL DEFAULT 0 COMMENT '对照上题库题目的题数',
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='背诵资料';

CREATE TABLE `recite_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `book_id`      BIGINT        NOT NULL,
  `set_no`       INT           NOT NULL COMMENT '第几套',
  `no`           INT           NOT NULL COMMENT '套内题号',
  `type`         TINYINT       NOT NULL COMMENT '1 单选 2 多选',
  `subject`      VARCHAR(16)   NULL COMMENT '科目：马原 / 毛概 / 习思想 / 史纲 / 思法 / 时政',
  `topic`        VARCHAR(128)  NULL COMMENT '考点归类',
  `qtype`        VARCHAR(16)   NULL COMMENT '记忆型 / 干扰项型 / 技巧型 / 阅读理解型',
  `stem`         TEXT          NOT NULL COMMENT '题干（纯文本）',
  `options`      TEXT          NULL COMMENT '选项 JSON：[{"key":"A","content":"..."}]',
  `answer`       VARCHAR(16)   NULL,
  `core`         VARCHAR(255)  NULL COMMENT '一句话核心考点',
  `cards`        TEXT          NULL COMMENT '要背的点 JSON：[{"q":"","a":""}]',
  `traps`        TEXT          NULL COMMENT '错误说法 JSON：[{"wrong":"","why":""}]',
  `dists`        TEXT          NULL COMMENT '易混辨析 JSON：[{"title":"","rows":[["",""]]}]',
  `tip`          VARCHAR(500)  NULL COMMENT '解题提示',
  `mnemonic`     VARCHAR(255)  NULL COMMENT '口诀',
  `question_id`  BIGINT        NULL COMMENT '对照上的题库题目（只读引用）',
  `chapter_id`   BIGINT        NULL COMMENT '对照上的题目所在章节',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_book_set` (`book_id`, `set_no`, `no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='背诵条目（每题一条）';

-- “错误说法”已掌握标记。按 资料 + 套 + 题号 + 第几条 记录，重新导入资料后标记仍然有效
CREATE TABLE `recite_trap_mark` (
  `id`          BIGINT    NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT    NOT NULL,
  `book_id`     BIGINT    NOT NULL,
  `set_no`      INT       NOT NULL,
  `no`          INT       NOT NULL,
  `idx`         INT       NOT NULL COMMENT '该题第几条错误说法，从 0 开始',
  `created_at`  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_trap` (`user_id`, `book_id`, `set_no`, `no`, `idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误说法已掌握标记';
