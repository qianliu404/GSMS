package com.gsms.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 该用户作为承办人/负责人仍在办（未结案、未归档）的案件数，删除前拦截 */
    @Select("SELECT COUNT(*) FROM case_main WHERE deleted = 0 " +
            "AND status NOT IN ('CLOSED','ARCHIVED') " +
            "AND (handler_id = #{userId} OR owner_id = #{userId})")
    long countActiveCases(@Param("userId") String userId);

    /** 登录名查重需包含已软删除的账号，避免复用登录名时撞唯一键 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE user_id = #{userId}")
    long countByUserIdIgnoreDeleted(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.user_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE u.deleted = 0 AND u.status = 1 AND r.role_code = 'ROLE_SYS_ADMIN'")
    long countEnabledSysAdmin();

    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodes(@Param("userId") String userId);

    @Select("SELECT r.data_scope FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectDataScopes(@Param("userId") String userId);

    /** 用户经角色获得的细粒度权限码（去重） */
    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 1")
    List<String> selectPermissionCodes(@Param("userId") String userId);

    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.menu_type = 'M' " +
            "ORDER BY m.sort")
    List<com.gsms.module.system.entity.SysMenu> selectMenusByUserId(@Param("userId") String userId);
}
