package com.lemon.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import com.lemon.biz.file.LocalFileStorage;
import com.lemon.security.AdminRole;
import com.lemon.security.StpAdminUtil;
import com.lemon.security.StpAppUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 用户端无需登录即可访问的接口：登录注册、首页、题库目录浏览 */
    private static final String[] APP_PUBLIC = {
            "/api/app/auth/**",
            "/api/app/home",
            "/api/app/app-config",
            "/api/app/access",
            "/api/app/modules/**",
            "/api/app/banks/**",
            "/api/app/punch-camps/**",
            "/api/app/documents/**",
            "/api/app/service-info",
    };

    @Value("${lemon.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Value("${lemon.upload.url-prefix}")
    private String uploadUrlPrefix;

    private final LocalFileStorage localFileStorage;

    public WebConfig(LocalFileStorage localFileStorage) {
        this.localFileStorage = localFileStorage;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            // ---- 管理端：先校验登录，再按角色 ----
            SaRouter.match("/api/admin/**")
                    .notMatch("/api/admin/auth/login")
                    .check(r -> StpAdminUtil.checkLogin());
            // 账号与角色、用户端用户、公告、系统参数、模块增删改：仅超级管理员
            SaRouter.match("/api/admin/admins/**", "/api/admin/roles/**",
                            "/api/admin/app-users/**", "/api/admin/notices/**", "/api/admin/configs/**",
                            "/api/admin/banners/**", "/api/admin/sms-logs/**",
                            "/api/admin/activation-codes/**", "/api/admin/module-users/**", "/api/admin/documents/**")
                    .check(r -> StpAdminUtil.LOGIC.checkRole(AdminRole.SUPER_ADMIN));
            SaRouter.match(SaHttpMethod.POST, SaHttpMethod.PUT, SaHttpMethod.DELETE)
                    .match("/api/admin/modules/**")
                    .check(r -> StpAdminUtil.LOGIC.checkRole(AdminRole.SUPER_ADMIN));
            // 其余管理接口（个人信息除外）：超级管理员或录题员
            SaRouter.match("/api/admin/**")
                    .notMatch("/api/admin/auth/**")
                    .check(r -> StpAdminUtil.LOGIC.checkRoleOr(AdminRole.SUPER_ADMIN, AdminRole.EDITOR));

            // ---- 用户端 ----
            SaRouter.match("/api/app/**")
                    .notMatch(APP_PUBLIC)
                    .check(r -> StpAppUtil.checkLogin());
        })).addPathPatterns("/api/**");
    }

    /** 本地上传目录对外提供访问（上线后由 Nginx 或对象存储提供，这里只服务开发环境） */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = uploadUrlPrefix.endsWith("/") ? uploadUrlPrefix : uploadUrlPrefix + "/";
        registry.addResourceHandler(prefix + "**")
                .addResourceLocations(localFileStorage.root().toUri().toString());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
