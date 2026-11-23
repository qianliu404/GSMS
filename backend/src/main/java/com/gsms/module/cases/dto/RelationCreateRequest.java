package com.gsms.module.cases.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 手动建立争议关联 */
@Data
public class RelationCreateRequest {

    @NotBlank(message = "主案案号不能为空")
    private String mainCaseNo;

    @NotBlank(message = "关联案案号不能为空")
    private String relatedCaseNo;

    @Size(max = 500, message = "关联说明不能超过 500 字")
    private String relationDesc;
}
