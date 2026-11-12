package com.lemon.biz.access.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.lemon.biz.access.entity.UserModuleAccess;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserModuleAccessMapper extends BaseMapper<UserModuleAccess> {

    /** 模块用户列表的一行：权限 + 最近一次开通（激活码 / 后台开通 / 老系统赠送）的记录 */
    @Data
    class ModuleUserRow {
        private Long id;
        private Long userId;
        private Long moduleId;
        private LocalDateTime expireAt;
        /** 第一次开通的时间 */
        private LocalDateTime createdAt;
        /** 最近一次开通：1 激活码 2 后台开通 / 延长 4 恢复老系统错题赠送 5 新用户注册赠送 */
        private Integer lastSource;
        private Long lastCodeId;
        private Long lastAdminId;
        private LocalDateTime lastAt;
        /** 开通 / 延长的次数 */
        private Integer grantCount;
        private String username;
        private String phone;
        private String nickname;
    }

    /** 各模块的人数 */
    @Data
    class ModuleCount {
        private Long moduleId;
        private Long active;
        private Long expiring;
        private Long expired;
    }

    /** 最近一次开通记录（只看开通类：1 激活码 2 后台开通 / 延长 4 老系统赠送 5 注册赠送） */
    String ROW_FROM = """
            FROM user_module_access a
            JOIN app_user u ON u.id = a.user_id AND u.deleted = 0
            LEFT JOIN module_access_log l ON l.id = (
                SELECT MAX(x.id) FROM module_access_log x
                WHERE x.user_id = a.user_id AND x.module_id = a.module_id AND x.source IN (1, 2, 4, 5))
            """;

    String ROW_SELECT = """
            SELECT a.id, a.user_id, a.module_id, a.expire_at, a.created_at,
                   l.source AS last_source, l.code_id AS last_code_id, l.admin_id AS last_admin_id, l.created_at AS last_at,
                   (SELECT COUNT(*) FROM module_access_log c
                    WHERE c.user_id = a.user_id AND c.module_id = a.module_id AND c.source IN (1, 2, 4, 5)) AS grant_count,
                   u.username, u.phone, u.nickname
            """;

    @Select(ROW_SELECT + ROW_FROM + "${ew.customSqlSegment}")
    IPage<ModuleUserRow> pageModuleUsers(IPage<ModuleUserRow> page, @Param(Constants.WRAPPER) Wrapper<ModuleUserRow> ew);

    @Select(ROW_SELECT + ROW_FROM + "${ew.customSqlSegment}")
    List<ModuleUserRow> listModuleUsers(@Param(Constants.WRAPPER) Wrapper<ModuleUserRow> ew);

    @Select("""
            SELECT a.module_id,
                   SUM(a.expire_at > #{now}) AS active,
                   SUM(a.expire_at > #{now} AND a.expire_at <= #{soon}) AS expiring,
                   SUM(a.expire_at <= #{now}) AS expired
            FROM user_module_access a
            JOIN app_user u ON u.id = a.user_id AND u.deleted = 0
            GROUP BY a.module_id
            """)
    List<ModuleCount> countByModule(@Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon);
}
