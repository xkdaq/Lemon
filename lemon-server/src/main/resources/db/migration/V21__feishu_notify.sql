-- 飞书群机器人推送：重要告警（风险告警、系统异常、短信发送失败）和每天 9 点的前一天汇总
-- webhook 地址相当于密钥，不写进代码仓库，部署后在后台「系统参数」里填写
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
  ('notify.feishu_webhook', '', '飞书群机器人 webhook 地址（重要告警和每日汇总推送到这里；留空不推送）'),
  ('notify.feishu_secret', '', '飞书群机器人签名密钥（机器人开启了「签名校验」才需要填，否则留空）');
