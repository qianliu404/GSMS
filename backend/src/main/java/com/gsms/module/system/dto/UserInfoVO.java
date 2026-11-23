package com.gsms.module.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserInfoVO {

    private Long id;
    private String userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private String mobile;
    private String email;
    private String position;
    private List<String> roles = new ArrayList<>();
    /** 细粒度权限码集合（模块:操作） */
    private List<String> permissions = new ArrayList<>();
    private String dataScope;
    private List<MenuVO> menus = new ArrayList<>();
    /** 是否可查看敏感字段明文（身份证、金额） */
    private boolean canViewSensitive;
}
