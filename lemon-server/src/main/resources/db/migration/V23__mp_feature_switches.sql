-- 小程序功能开关（网页端不受影响）。小程序是否显示激活码入口：1 显示 / 0 隐藏，默认隐藏（审核不允许站外付费解锁的引导）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('app.mp_activation', '0', '小程序显示激活码入口（1 显示 / 0 隐藏；网页端始终显示）');

-- 小程序是否显示「恢复老系统错题」入口：默认隐藏（输入激活码 / 订单号，审核时容易被当成付费解锁）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('app.mp_restore', '0', '小程序显示「恢复老系统错题」入口（1 显示 / 0 隐藏；网页端始终显示）');
