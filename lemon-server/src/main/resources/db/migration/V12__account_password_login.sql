-- 账号改为「账号 + 密码」为主：用户名、后台重置密码标记、短信用途说明
-- 用户名：注册（Web）/ 首次授权登录（小程序）时自动分配，不可修改；可用「用户名 + 密码」登录。
-- 规则：lemon + 数字，以字母开头，和手机号区分。已有用户按 id 补发（lemon000012），新用户随机 6 位（100000~999999，不会和补发的撞）。
-- must_change_password：后台重置密码后置 1，用户登录后提示修改，改完清零。
ALTER TABLE `app_user`
  ADD COLUMN `username` VARCHAR(32) NULL COMMENT '用户名，自动分配，不可修改' AFTER `id`,
  ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '1 = 后台重置过密码，需提示用户修改' AFTER `password`;

UPDATE `app_user` SET `username` = CONCAT('lemon', LPAD(`id`, 6, '0')) WHERE `username` IS NULL;

ALTER TABLE `app_user` ADD UNIQUE KEY `uk_username` (`username`);

-- 短信用途：验证码登录、登录后设置密码已下线，新增注册、找回密码
ALTER TABLE `sms_log` MODIFY COLUMN `scene` TINYINT NOT NULL COMMENT '1 验证码登录（旧） 2 设置密码（旧） 3 注册 4 找回密码';
