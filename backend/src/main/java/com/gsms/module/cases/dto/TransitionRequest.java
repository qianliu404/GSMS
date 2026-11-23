package com.gsms.module.cases.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class TransitionRequest {

    /** 目标状态；留空时按状态机唯一后继推进 */
    private String toStatus;

    private String action;

    private String opinion;

    /**
     * 本节点办理结论：随流转一并写入案件档案，实现「办完即回填」。
     * 各字段按当前所处节点取用，不相关的节点留空即可。
     */
    /** 工伤认定结论：RECOGNIZED / NOT_RECOGNIZED */
    private String recognizeResult;
    /** 劳动能力鉴定结论，如「九级」 */
    private String appraisalLevel;
    /** 鉴定休假天数（天） */
    private Integer restDays;
    /** 仲裁受理日期 yyyy-MM-dd */
    private String acceptDate;
    /** 履行款金额 */
    private BigDecimal performAmount;
    /** 仲裁结果：AWARD / MEDIATION / WITHDRAW */
    private String resultType;

    @Data
    public static class RollbackRequest {
        @NotBlank(message = "退回原因不能为空")
        private String opinion;
        /** 退回目标节点；留空时回到上一节点 */
        private String toStatus;
    }

    @Data
    public static class CloseRequest {
        private String opinion;
        /** 结案方式：TREATMENT_SETTLED/MEDIATION/AWARD/WITHDRAW/OTHER */
        private String closeType;
        /** 卷宗页数 */
        private Integer archivePages;
        /** 保管期限（年） */
        private Integer archiveYears;
        /** 履行款：从「履行」节点直接结案时随结案一起登记 */
        private BigDecimal performAmount;
    }

    @Data
    public static class RelationRequest {
        @NotBlank(message = "被关联案件不能为空")
        private String relatedCaseNo;
        /** 关联类型：已不再是必填，缺省按 RELATED 处理 */
        private String relationType;
        private String relationDesc;
    }
}
