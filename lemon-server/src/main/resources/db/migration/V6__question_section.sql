-- 题目“小节”：章节 / 试卷下按小题型分组，如 论述题、材料分析题；为空时按题型（单选题 / 多选题 / 判断题 / 分析题）分组
ALTER TABLE `question`
  ADD COLUMN `section` VARCHAR(32) NULL COMMENT '小节（如 论述题、材料分析题），为空按题型' AFTER `type`;

-- 按小节练习：用小节名替代 V5 的题型
ALTER TABLE `practice_session`
  DROP COLUMN `question_type`,
  ADD COLUMN `section` VARCHAR(32) NULL COMMENT '按小节练习时的小节名，NULL 为整章' AFTER `mode`;
