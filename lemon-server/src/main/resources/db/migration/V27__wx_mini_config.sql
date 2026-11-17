-- 微信小程序 AppID / AppSecret 改为在后台「系统参数」里填写（留空时仍读服务器环境变量 WX_APPID / WX_SECRET）
-- AppSecret 相当于密码：后台列表只显示后 4 位，保存时先向微信校验
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('wx.appid', '', '微信小程序 AppID（小程序后台 → 开发管理 → 开发设置；留空则用服务器环境变量 WX_APPID）'),
  ('wx.secret', '', '微信小程序 AppSecret（只显示后 4 位，填新值覆盖；留空则用服务器环境变量 WX_SECRET）');
