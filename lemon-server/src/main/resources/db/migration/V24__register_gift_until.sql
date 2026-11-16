-- 新用户注册赠送「政治」「教育学」从「N 天」改成「到固定日期 00:00:00 到期」（系统参数 app.register_gift_until，留空 = 不赠送）
-- 已经注册赠送过的用户到期时间不变
DELETE FROM `sys_config` WHERE `config_key` = 'app.register_gift_days';

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('app.register_gift_until', '2027-01-01', '新用户注册赠送「政治」「教育学」的到期日期（在这一天 00:00:00 到期，格式 2027-01-01；留空 = 不赠送）');
