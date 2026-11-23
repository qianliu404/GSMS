package com.gsms.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录态用户。由 JWT 解析后放入 SecurityContext。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** sys_user.id */
    private Long id;
    /** 登录名 */
    private String userId;
    /** 姓名 */
    private String userName;
    /** 部门ID */
    private Long deptId;
    /** 角色编码集合 */
    private List<String> roles = new ArrayList<>();
    /** 细粒度权限码集合（模块:操作），与角色并行校验 */
    private List<String> permissions = new ArrayList<>();
    /** 数据范围：ALL / DEPT / SELF */
    private String dataScope = "SELF";

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    public boolean hasPermission(String permCode) {
        return permissions != null && permissions.contains(permCode);
    }

    public boolean isAllScope() {
        return "ALL".equals(dataScope);
    }

    /** 是否可查看全部案件的敏感字段（管理员/案件管理员/部门负责人按本部门另判） */
    public boolean canViewSensitive() {
        return isAllScope();
    }
}
