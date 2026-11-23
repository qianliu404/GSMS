package com.gsms.module.recycle.dto;

import lombok.Data;

/** 回收站查询参数 */
@Data
public class RecycleQuery {

    /** CASE / USER */
    private String target = "CASE";
    private String keyword;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
