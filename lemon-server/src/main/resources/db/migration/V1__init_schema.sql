-- =============================================================
-- Lemon 刷题 初始表结构
-- 层级：模块(module) → 题库(question_bank) → 科目(course，仅科目型) → 章节(chapter) → 题目(question)
-- =============================================================

-- ---------- 内容域 ----------
CREATE TABLE `module` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(32)  NOT NULL COMMENT '名称',
  `icon`        VARCHAR(255) NULL COMMENT '图标',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块（政治/教育学）';

CREATE TABLE `question_bank` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `module_id`      BIGINT       NOT NULL COMMENT '所属模块',
  `name`           VARCHAR(64)  NOT NULL COMMENT '名称',
  `type`           TINYINT      NOT NULL COMMENT '1 科目型 2 试卷型',
  `cover`          VARCHAR(255) NULL COMMENT '封面',
  `intro`          VARCHAR(500) NULL COMMENT '简介',
  `sort`           INT          NOT NULL DEFAULT 0,
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  `question_count` INT          NOT NULL DEFAULT 0 COMMENT '题量（冗余）',
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

CREATE TABLE `course` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `bank_id`     BIGINT      NOT NULL COMMENT '所属题库',
  `name`        VARCHAR(64) NOT NULL COMMENT '名称',
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      TINYINT     NOT NULL DEFAULT 1,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bank` (`bank_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科目（仅科目型题库）';

CREATE TABLE `chapter` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `bank_id`        BIGINT       NOT NULL COMMENT '所属题库',
  `course_id`      BIGINT       NULL COMMENT '所属科目，试卷型为 NULL',
  `name`           VARCHAR(128) NOT NULL COMMENT '名称',
  `sort`           INT          NOT NULL DEFAULT 0,
  `status`         TINYINT      NOT NULL DEFAULT 1,
  `time_limit`     INT          NULL COMMENT '试卷型：考试时长（分钟）',
  `total_score`    INT          NULL COMMENT '试卷型：总分',
  `question_count` INT          NOT NULL DEFAULT 0 COMMENT '题量（冗余）',
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bank_course` (`bank_id`, `course_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节（试卷型一章即一套卷）';

CREATE TABLE `question` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `chapter_id`  BIGINT        NOT NULL COMMENT '所属章节',
  `bank_id`     BIGINT        NOT NULL COMMENT '所属题库（冗余）',
  `module_id`   BIGINT        NOT NULL COMMENT '所属模块（冗余）',
  `type`        TINYINT       NOT NULL COMMENT '1 单选 2 多选 3 判断 4 主观',
  `stem`        TEXT          NOT NULL COMMENT '题干',
  `options`     JSON          NULL COMMENT '选项 [{"key":"A","content":"..."}]',
  `answer`      VARCHAR(500)  NULL COMMENT '答案',
  `analysis`    TEXT          NULL COMMENT '解析/参考答案',
  `score`       DECIMAL(5,1)  NOT NULL DEFAULT 0 COMMENT '分值',
  `difficulty`  TINYINT       NOT NULL DEFAULT 2 COMMENT '难度 1-3',
  `source`      VARCHAR(64)   NULL COMMENT '来源',
  `sort`        INT           NOT NULL DEFAULT 0,
  `status`      TINYINT       NOT NULL DEFAULT 1,
  `deleted`     TINYINT       NOT NULL DEFAULT 0,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chapter` (`chapter_id`, `status`, `sort`),
  KEY `idx_bank` (`bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目';

-- ---------- 用户域 ----------
CREATE TABLE `app_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `phone`         VARCHAR(20)  NULL COMMENT '手机号',
  `password`      VARCHAR(100) NULL COMMENT 'BCrypt',
  `openid`        VARCHAR(64)  NULL COMMENT '小程序 openid',
  `unionid`       VARCHAR(64)  NULL,
  `nickname`      VARCHAR(64)  NULL,
  `avatar`        VARCHAR(255) NULL,
  `register_from` TINYINT      NOT NULL DEFAULT 1 COMMENT '1 Web 2 小程序',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用',
  `last_login_at` DATETIME     NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端用户';

CREATE TABLE `practice_session` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       NOT NULL,
  `chapter_id`     BIGINT       NULL COMMENT '错题/收藏练习时为 NULL',
  `mode`           TINYINT      NOT NULL COMMENT '1 练习 2 背题 3 考试 4 错题重练 5 收藏练习',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 进行中 1 已完成',
  `question_ids`   JSON         NULL COMMENT '本次题目 id 列表',
  `current_index`  INT          NOT NULL DEFAULT 0,
  `correct_count`  INT          NOT NULL DEFAULT 0,
  `answered_count` INT          NOT NULL DEFAULT 0,
  `score`          DECIMAL(6,1) NULL,
  `started_at`     DATETIME     NULL,
  `finished_at`    DATETIME     NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_chapter` (`user_id`, `chapter_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习/考试会话';

CREATE TABLE `answer_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `question_id` BIGINT       NOT NULL,
  `chapter_id`  BIGINT       NOT NULL,
  `session_id`  BIGINT       NULL,
  `user_answer` VARCHAR(500) NULL,
  `is_correct`  TINYINT      NULL COMMENT '1 对 0 错',
  `cost_ms`     INT          NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_q` (`user_id`, `question_id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题明细';

CREATE TABLE `user_chapter_progress` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT   NOT NULL,
  `chapter_id`    BIGINT   NOT NULL,
  `done_count`    INT      NOT NULL DEFAULT 0,
  `correct_count` INT      NOT NULL DEFAULT 0,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chapter` (`user_id`, `chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户章节进度';

CREATE TABLE `user_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `question_id` BIGINT   NOT NULL,
  `module_id`   BIGINT   NOT NULL,
  `bank_id`     BIGINT   NOT NULL,
  `chapter_id`  BIGINT   NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_q` (`user_id`, `question_id`),
  KEY `idx_user_module` (`user_id`, `module_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏集';

CREATE TABLE `user_wrong_question` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT       NOT NULL,
  `question_id`       BIGINT       NOT NULL,
  `module_id`         BIGINT       NOT NULL,
  `bank_id`           BIGINT       NOT NULL,
  `chapter_id`        BIGINT       NOT NULL,
  `wrong_count`       INT          NOT NULL DEFAULT 1,
  `right_streak`      INT          NOT NULL DEFAULT 0 COMMENT '连续答对次数',
  `last_wrong_answer` VARCHAR(500) NULL,
  `last_wrong_at`     DATETIME     NULL,
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1 在错题集 0 已移出',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_q` (`user_id`, `question_id`),
  KEY `idx_user_module` (`user_id`, `module_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题集';

-- ---------- 运营域 ----------
CREATE TABLE `banner` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `module_id`   BIGINT       NULL COMMENT 'NULL 为首页',
  `image`       VARCHAR(255) NOT NULL,
  `link`        VARCHAR(255) NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Banner';

CREATE TABLE `notice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT         NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

CREATE TABLE `feedback` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT        NULL,
  `question_id` BIGINT        NULL COMMENT '题目纠错时有值',
  `type`        TINYINT       NOT NULL COMMENT '1 题目纠错 2 意见建议',
  `content`     VARCHAR(1000) NOT NULL,
  `images`      JSON          NULL,
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已处理',
  `reply`       VARCHAR(500)  NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈/纠错';

CREATE TABLE `sys_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64)  NOT NULL,
  `config_value` VARCHAR(500) NOT NULL,
  `remark`       VARCHAR(255) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

-- ---------- 系统域（RBAC，菜单权限二期细化） ----------
CREATE TABLE `sys_admin` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(32)  NOT NULL,
  `password`      VARCHAR(100) NOT NULL,
  `nickname`      VARCHAR(32)  NULL,
  `status`        TINYINT      NOT NULL DEFAULT 1,
  `last_login_at` DATETIME     NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员';

CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32) NOT NULL,
  `name`        VARCHAR(32) NOT NULL,
  `status`      TINYINT     NOT NULL DEFAULT 1,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE `sys_admin_role` (
  `admin_id` BIGINT NOT NULL,
  `role_id`  BIGINT NOT NULL,
  PRIMARY KEY (`admin_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色';

-- ---------- 初始数据 ----------
INSERT INTO `module` (`name`, `sort`) VALUES ('政治', 1), ('教育学', 2);

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('wrong.remove.streak', '2', '错题连续答对 N 次后自动移出错题集');

INSERT INTO `sys_role` (`code`, `name`) VALUES ('super_admin', '超级管理员'), ('editor', '录题员');
