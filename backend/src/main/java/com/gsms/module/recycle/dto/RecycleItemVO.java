package com.gsms.module.recycle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 回收站列表项（案件与用户共用，字段按目标类型填充） */
@Data
public class RecycleItemVO {

    private Long id;
    /** 案号 / 登录名 */
    private String code;
    /** 案件名称 / 姓名 */
    private String title;
    /** 当事人 / 所属部门 */
    private String subtitle;
    /** 类型码（案件）/ 岗位（用户） */
    private String tag;
    /** 承办人 / 手机号 */
    private String ownerName;
    private LocalDateTime deletedTime;
}
