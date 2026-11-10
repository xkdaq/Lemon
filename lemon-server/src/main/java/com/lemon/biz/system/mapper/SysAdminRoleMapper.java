package com.lemon.biz.system.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理员-角色关联（联合主键表，不走 BaseMapper）
 */
@Mapper
public interface SysAdminRoleMapper {

    /** 管理员的有效角色编码 */
    @Select("""
            SELECT r.code FROM sys_admin_role ar JOIN sys_role r ON r.id = ar.role_id
            WHERE ar.admin_id = #{adminId} AND r.status = 1 AND r.deleted = 0
            """)
    List<String> selectRoleCodes(@Param("adminId") Long adminId);

    @Select("""
            <script>
            SELECT ar.admin_id AS adminId, r.code AS code FROM sys_admin_role ar JOIN sys_role r ON r.id = ar.role_id
            WHERE r.deleted = 0 AND ar.admin_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<AdminRoleRow> selectRoleCodesByAdmins(@Param("ids") Collection<Long> adminIds);

    /** 拥有某角色的、启用状态的管理员数量 */
    @Select("""
            SELECT COUNT(*) FROM sys_admin_role ar
            JOIN sys_role r ON r.id = ar.role_id
            JOIN sys_admin a ON a.id = ar.admin_id
            WHERE r.code = #{code} AND a.status = 1 AND a.deleted = 0
            """)
    long countActiveAdminsWithRole(@Param("code") String code);

    @Delete("DELETE FROM sys_admin_role WHERE admin_id = #{adminId}")
    int deleteByAdmin(@Param("adminId") Long adminId);

    @Insert("""
            <script>
            INSERT INTO sys_admin_role (admin_id, role_id)
            SELECT #{adminId}, id FROM sys_role WHERE deleted = 0 AND code IN
            <foreach collection="codes" item="c" open="(" separator="," close=")">#{c}</foreach>
            </script>
            """)
    int insertByCodes(@Param("adminId") Long adminId, @Param("codes") Collection<String> roleCodes);

    record AdminRoleRow(Long adminId, String code) {
    }
}
