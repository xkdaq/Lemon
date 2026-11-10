package com.lemon.security;

/**
 * 管理员角色编码（与 sys_role.code 一致）。
 * <ul>
 *   <li>super_admin：全部权限，含模块增删改、管理员账号管理</li>
 *   <li>editor（录题员）：题库 / 科目 / 章节 / 题目管理，模块只读</li>
 * </ul>
 */
public final class AdminRole {

    public static final String SUPER_ADMIN = "super_admin";
    public static final String EDITOR = "editor";

    private AdminRole() {
    }
}
