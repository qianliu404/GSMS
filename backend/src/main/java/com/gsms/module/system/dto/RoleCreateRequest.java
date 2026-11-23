package com.gsms.module.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 新建自定义角色 */
@Data
public class RoleCreateRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不超过 50 字")
    private String roleName;

    /** ALL / DEPT / SELF，默认 SELF */
    private String dataScope;

    /** 可选：复制该角色的权限与菜单作为初始配置 */
    private Long copyFromRoleId;

    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
