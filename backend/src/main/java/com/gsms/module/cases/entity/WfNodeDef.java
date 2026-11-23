package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点定义。状态码由 CaseStateMachine 约束流转合法性，
 * 本表只承载节点展示与办理要素（时限、主责角色、材料、系统动作、可退回）。
 */
@Data
@TableName("wf_node_def")
public class WfNodeDef {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** INJURY / ARBITRATION */
    private String flowKey;
    private String statusCode;
    private String nodeName;
    private Integer sortNo;
    private String timeLimit;
    private String mainRole;
    private String keyMaterial;
    private String sysAction;
    private Integer allowRollback;
    /** 允许的后继节点码，逗号分隔；为空则沿用 CaseStateMachine 内置拓扑 */
    private String nextCodes;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
