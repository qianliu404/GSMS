package com.gsms.module.cases.dto;

import com.gsms.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LedgerQuery extends PageQuery {

    private String keyword;
    /** INJURY / ARBITRATION，空为全部 */
    private String caseType;
    private String plantArea;
    /** RECOGNIZED / NOT_RECOGNIZED / DECLARING / NOT_DECLARED */
    private String recognizeResult;
    /** NOT_APPLIED / APPLYING / RECEIVED / INELIGIBLE */
    private String subsidyStatus;
}
