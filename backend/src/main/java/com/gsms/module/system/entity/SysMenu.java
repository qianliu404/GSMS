package com.gsms.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String menuCode;
    private String menuName;
    /** M 菜单 / B 按钮 */
    private String menuType;
    private Long parentId;
    private String path;
    private String permCode;
    private String icon;
    private Integer sort;
    private Integer status;
}
