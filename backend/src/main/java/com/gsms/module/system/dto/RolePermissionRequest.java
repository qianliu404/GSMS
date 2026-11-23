package com.gsms.module.system.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/** 系统管理员保存某角色的权限项 */
@Data
public class RolePermissionRequest {

    @NotNull(message = "权限码列表不能为空")
    private List<String> permCodes = new ArrayList<>();
}
