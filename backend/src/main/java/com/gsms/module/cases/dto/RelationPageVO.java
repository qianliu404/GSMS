package com.gsms.module.cases.dto;

import com.gsms.common.PageQuery;
import com.gsms.common.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 争议关联页：统计卡 + 分页列表 */
@Data
public class RelationPageVO {

    /** 关联关系数（按案件对去重） */
    private int relationCount;
    /** 进行中关联：至少一方未结案且未归档 */
    private int activeCount;
    /** 关联当事人（去重人数） */
    private int employeeCount;

    private PageResult<RelationVO> page;

    /** 查询条件 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageQuery {
        /** 案件类型：命中主案或关联案任一类型 */
        private String caseType;
        /** 案号 / 当事人 */
        private String keyword;
    }
}
