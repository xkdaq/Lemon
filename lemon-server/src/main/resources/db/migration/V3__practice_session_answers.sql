-- 做题会话：保存本次每道题的作答（续做、考试草稿、结果页都靠它），考试时长在开考时固定下来
ALTER TABLE `practice_session`
  ADD COLUMN `answers`    JSON NULL COMMENT '本次作答 {"题目id":{"a":"用户答案","c":1对/0错/null未判}}' AFTER `question_ids`,
  ADD COLUMN `time_limit` INT  NULL COMMENT '考试模式：时长（分钟），开考时从章节复制' AFTER `score`;

-- 章节进度按“用户 + 章节”汇总答题记录
CREATE INDEX `idx_user_chapter` ON `answer_record` (`user_id`, `chapter_id`);
