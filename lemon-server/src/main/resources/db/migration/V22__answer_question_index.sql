-- 做题页显示每道题的全站作答次数和正确率：按题目统计需要的索引（覆盖索引，只读索引即可）
CREATE INDEX `idx_question_correct` ON `answer_record` (`question_id`, `is_correct`);
