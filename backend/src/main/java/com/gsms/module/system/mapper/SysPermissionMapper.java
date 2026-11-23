package com.gsms.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.system.entity.SysPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 角色已授权的权限码 */
    @Select("SELECT p.perm_code FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId} AND p.status = 1 " +
            "ORDER BY p.sort, p.id")
    List<String> selectCodesByRoleId(@Param("roleId") Long roleId);
}
