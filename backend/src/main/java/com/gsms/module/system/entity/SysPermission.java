package com.gsms.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 模块操作权限项 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String moduleName;
    private String action;
    private String actionName;
    private String permCode;
    private Integer sort;
    private Integer status;
}
