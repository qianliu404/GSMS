package com.gsms.module.cases.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class NodeDefRequest {

    @NotBlank(message = "流程不能为空")
    @Pattern(regexp = "INJURY|ARBITRATION", message = "流程只能是 INJURY/ARBITRATION")
    private String flowKey;

    /** 新增时必填，对应状态机节点码 */
    @Size(max = 30, message = "状态码不能超过 30 字")
    private String statusCode;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 50, message = "节点名称不能超过 50 字")
    private String nodeName;

    @Size(max = 20, message = "办理时限不能超过 20 字")
    private String timeLimit;

    @Size(max = 100, message = "主责角色不能超过 100 字")
    private String mainRole;

    @Size(max = 300, message = "关键材料不能超过 300 字")
    private String keyMaterial;

    @Size(max = 300, message = "系统动作不能超过 300 字")
    private String sysAction;

    private Boolean allowRollback;

    /** 后继节点码，逗号分隔（如 "PERFORM,APPEAL"）；留空则沿用内置流程 */
    @Size(max = 200, message = "后继节点配置不能超过 200 字")
    private String nextCodes;

    /** 新增时的插入位置：放在第 N 位（1 表示最前，末位为归档前） */
    private Integer insertAfter;
}
