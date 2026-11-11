-- 手机号是否验证过（短信验证码 / 小程序手机号快速验证）。
-- 手机号是跨端唯一身份：小程序登录时按验证过的手机号并入 Web 端同手机号账号。
-- 之前用「手机号 + 密码」注册的账号没验证过手机号，第一次验证时会让旧密码失效，防止别人抢注的密码继续能登录。
ALTER TABLE `app_user`
  ADD COLUMN `phone_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '手机号是否已验证：1 是 0 否' AFTER `phone`;
