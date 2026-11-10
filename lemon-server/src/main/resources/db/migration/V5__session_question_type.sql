-- 按题型练习（章节下的“节”：单选 / 多选 / 分析题）：会话记录练的是哪种题型，NULL 为整章
ALTER TABLE `practice_session`
  ADD COLUMN `question_type` TINYINT NULL COMMENT '按题型练习时的题型，NULL 为整章' AFTER `mode`;
