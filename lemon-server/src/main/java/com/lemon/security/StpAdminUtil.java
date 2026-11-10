package com.lemon.security;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 管理系统登录体系。loginId = sys_admin.id
 */
public final class StpAdminUtil {

    public static final String TYPE = "admin";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    private StpAdminUtil() {
    }

    public static void login(Long adminId) {
        LOGIC.login(adminId);
    }

    public static String token() {
        return LOGIC.getTokenValue();
    }

    public static void checkLogin() {
        LOGIC.checkLogin();
    }

    public static Long adminId() {
        return LOGIC.getLoginIdAsLong();
    }

    public static void logout() {
        LOGIC.logout();
    }
}
