-- =============================================================
-- 老系统（迅课）错题恢复
-- 老用户凭激活码 / 订单号认领自己的老账号，把老错题（已对应到现在的题库题目）恢复进错题集
-- 只存核验和恢复需要的数据：不存手机号、OpenID 等
-- =============================================================

CREATE TABLE `legacy_user` (
  `old_user_id` BIGINT      NOT NULL COMMENT '老系统用户 ID',
  `nickname`    VARCHAR(64) NULL COMMENT '老系统昵称（已打码），认领前给用户确认',
  `wrong_total` INT         NOT NULL DEFAULT 0 COMMENT '老错题题目数',
  `matched`     INT         NOT NULL DEFAULT 0 COMMENT '能对应到现在题库的题目数',
  `claimed_by`  BIGINT      NULL COMMENT '认领的新用户 ID；一个老账号只能被认领一次',
  `claimed_at`  DATETIME    NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`old_user_id`),
  KEY `idx_claimed_by` (`claimed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老系统用户（错题恢复用）';

CREATE TABLE `legacy_claim_key` (
  `claim_key`   VARCHAR(64) NOT NULL COMMENT '激活码（大写）/ 订单号 / 微信交易单号',
  `key_type`    TINYINT     NOT NULL COMMENT '1 激活码 2 订单号 3 微信交易单号',
  `old_user_id` BIGINT      NOT NULL,
  PRIMARY KEY (`claim_key`),
  KEY `idx_old_user` (`old_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老用户核验凭证';

CREATE TABLE `legacy_wrong` (
  `old_user_id` BIGINT      NOT NULL,
  `question_id` BIGINT      NOT NULL COMMENT '对应的现在题库的题目',
  `wrong_count` INT         NOT NULL DEFAULT 1 COMMENT '老系统答错次数',
  `last_answer` VARCHAR(64) NULL COMMENT '最近一次错误作答',
  PRIMARY KEY (`old_user_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老错题（已对应到现在的题目）';
