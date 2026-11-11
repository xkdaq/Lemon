-- 分值规则按模块设置：单选（含判断）、多选每题多少分；主观题不计分。
-- 模拟考试只考选择题，试卷满分 = 选择题分值之和（运行时计算，chapter.total_score 不再使用）。
-- 政治：单选 1 分、多选 2 分；教育学等其他模块：选择题每题 2 分。
ALTER TABLE `module`
  ADD COLUMN `single_score` DECIMAL(5, 1) NOT NULL DEFAULT 2.0 COMMENT '单选题 / 判断题每题分值' AFTER `free`,
  ADD COLUMN `multi_score`  DECIMAL(5, 1) NOT NULL DEFAULT 2.0 COMMENT '多选题每题分值' AFTER `single_score`;

UPDATE `module` SET `single_score` = 1.0, `multi_score` = 2.0 WHERE `name` = '政治';

-- 已有题目按规则重算分值
UPDATE `question` q JOIN `module` m ON m.`id` = q.`module_id`
SET q.`score` = CASE q.`type` WHEN 1 THEN m.`single_score` WHEN 3 THEN m.`single_score` WHEN 2 THEN m.`multi_score` ELSE 0 END;
