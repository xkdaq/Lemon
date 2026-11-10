-- 删除管理员时会在用户名后追加 "#del{id}" 以释放唯一索引，需要更长的字段
ALTER TABLE `sys_admin` MODIFY `username` VARCHAR(64) NOT NULL;
