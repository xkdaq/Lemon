package com.lemon.security;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 用户端（Web / 小程序）登录体系。loginId = app_user.id
 */
public final class StpAppUtil {

    public static final String TYPE = "app";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    private StpAppUtil() {
    }

    public static void login(Long userId) {
        LOGIC.login(userId);
    }

    public static String token() {
        return LOGIC.getTokenValue();
    }

    public static void checkLogin() {
        LOGIC.checkLogin();
    }

    public static boolean isLogin() {
        return LOGIC.isLogin();
    }

    public static Long userId() {
        return LOGIC.getLoginIdAsLong();
    }

    /** 未登录返回 null，用于允许匿名访问但登录后返回个性化数据（进度等）的接口 */
    public static Long userIdOrNull() {
        return LOGIC.isLogin() ? LOGIC.getLoginIdAsLong() : null;
    }

    public static void logout() {
        LOGIC.logout();
    }
}
