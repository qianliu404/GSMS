package com.gsms.module.cases.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 流程总览：统计卡 + 两条流程条 + 待办预警 */
@Data
public class DashboardVO {

    /** 在办案件（未结案） */
    private int activeCount;
    private int injuryActiveCount;
    private int arbitrationActiveCount;
    /** 节点预警（含超期） */
    private int warningCount;
    private int overdueCount;
    /** 本年度已结案 */
    private int yearClosedCount;
    private long avgCloseDays;
    /** 累计赔付（工伤待遇 + 仲裁裁决/调解） */
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal injuryAmount = BigDecimal.ZERO;
    private BigDecimal arbitrationAmount = BigDecimal.ZERO;

    private List<FlowVO> flows = new ArrayList<>();
    private List<TodoVO> todos = new ArrayList<>();

    @Data
    public static class FlowVO {
        private String key;
        private String name;
        private int total;
        private List<StageVO> stages = new ArrayList<>();
    }

    @Data
    public static class StageVO {
        private String code;
        private String name;
        private String timeLimit;
        private int count;
        /** 允许的后继状态码，用于前端渲染分支结构 */
        private List<String> next;
    }

    @Data
    public static class TodoVO {
        private Long caseId;
        private String caseNo;
        private String employeeName;
        private String stageName;
        private String handlerName;
        private String deadlineDate;
        /** true 已超期 */
        private boolean overdue;
        private long days;
    }
}
