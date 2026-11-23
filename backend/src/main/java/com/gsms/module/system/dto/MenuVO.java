package com.gsms.module.system.dto;

import lombok.Data;

@Data
public class MenuVO {

    private Long id;
    private String menuCode;
    private String menuName;
    private String path;
    private String permCode;
    private String icon;
    private Integer sort;
}
