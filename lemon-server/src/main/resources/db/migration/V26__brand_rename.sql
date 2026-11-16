-- 品牌改名：「Lemon 刷题」→「柠檬题海」（与微信小程序同名）。只替换协议正文里的名字，管理员在后台改过的其他内容保留
UPDATE `app_document` SET `content` = REPLACE(`content`, 'Lemon 刷题', '柠檬题海') WHERE `content` LIKE '%Lemon 刷题%';
UPDATE `sys_config` SET `config_value` = REPLACE(`config_value`, 'Lemon 刷题', '柠檬题海') WHERE `config_value` LIKE '%Lemon 刷题%';
