package com.gsms.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String userName;
    private Long deptId;
    private String mobile;
    private String email;
    private String position;
    /** 密码哈希，任何接口都不得返回 */
    @JsonIgnore
    private String passwordHash;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
