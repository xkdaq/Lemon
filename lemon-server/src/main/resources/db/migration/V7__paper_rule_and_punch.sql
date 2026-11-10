-- =============================================================
-- 组卷规则 + 打卡
-- 组卷规则：在题库（可限定章节）里按题型数量、难度比例随机抽题
-- 打卡活动：每天按组卷规则抽一套题，做完即打卡；支持休息日、补卡、按日覆盖规则
-- =============================================================

CREATE TABLE `paper_rule` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `bank_id`          BIGINT      NOT NULL COMMENT '抽题题库',
  `name`             VARCHAR(64) NOT NULL COMMENT '名称',
  `chapter_ids`      JSON        NULL COMMENT '抽题范围（章节 id 列表），NULL 为整个题库',
  `items`            JSON        NOT NULL COMMENT '各题型抽题数 [{"type":1,"count":10}]',
  `difficulty_ratio` JSON        NULL COMMENT '难度比例 [易,中,难]（百分比，合计 100），NULL 不限',
  `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  `deleted`          TINYINT     NOT NULL DEFAULT 0,
  `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bank` (`bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组卷规则';

CREATE TABLE `punch_camp` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(64)  NOT NULL COMMENT '名称',
  `cover`         VARCHAR(255) NULL COMMENT '封面',
  `intro`         VARCHAR(2000) NULL COMMENT '介绍',
  `paper_rule_id` BIGINT       NOT NULL COMMENT '默认组卷规则（每天按它抽题）',
  `start_date`    DATE         NOT NULL COMMENT '开始日期',
  `end_date`      DATE         NOT NULL COMMENT '结束日期（含）',
  `rest_weekdays` VARCHAR(20)  NULL COMMENT '休息日，周一到周日为 1-7，逗号分隔',
  `makeup_limit`  INT          NOT NULL DEFAULT 0 COMMENT '每人可补卡次数，0 不允许补卡',
  `sort`          INT          NOT NULL DEFAULT 0,
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡活动';

CREATE TABLE `punch_schedule` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `camp_id`       BIGINT   NOT NULL,
  `day`           DATE     NOT NULL,
  `paper_rule_id` BIGINT   NOT NULL COMMENT '当天使用的组卷规则（覆盖活动默认规则）',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_camp_day` (`camp_id`, `day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡日程（只存覆盖了默认规则的日期）';

CREATE TABLE `punch_member` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `camp_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `makeup_used` INT      NOT NULL DEFAULT 0 COMMENT '已用补卡次数',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_camp_user` (`camp_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡学员';

CREATE TABLE `punch_record` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `camp_id`       BIGINT   NOT NULL,
  `user_id`       BIGINT   NOT NULL,
  `day`           DATE     NOT NULL COMMENT '打卡日期',
  `session_id`    BIGINT   NOT NULL COMMENT '做题会话',
  `status`        TINYINT  NOT NULL DEFAULT 0 COMMENT '0 做题中 1 已打卡',
  `makeup`        TINYINT  NOT NULL DEFAULT 0 COMMENT '1 补卡',
  `total`         INT      NOT NULL DEFAULT 0 COMMENT '题数',
  `correct_count` INT      NOT NULL DEFAULT 0 COMMENT '答对数',
  `finished_at`   DATETIME NULL COMMENT '打卡完成时间',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_camp_user_day` (`camp_id`, `user_id`, `day`),
  UNIQUE KEY `uk_session` (`session_id`),
  KEY `idx_camp_day` (`camp_id`, `day`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录';
