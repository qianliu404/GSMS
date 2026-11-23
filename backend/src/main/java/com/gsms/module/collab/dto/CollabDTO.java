package com.gsms.module.collab.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CollabDTO {

    @Data
    public static class TaskCreateRequest {
        @NotBlank(message = "任务类型不能为空")
        private String taskType;
        @NotBlank(message = "责任人不能为空")
        private String assigneeId;
        @NotNull(message = "截止时间不能为空")
        private LocalDateTime deadline;
        /** HIGH / MEDIUM / LOW */
        private String priority = "MEDIUM";
    }

    @Data
    public static class TaskVO {
        private Long id;
        private Long caseId;
        private String caseNo;
        private String caseName;
        private String taskType;
        private String assignerId;
        private String assignerName;
        private String assigneeId;
        private String assigneeName;
        private LocalDateTime deadline;
        private String priority;
        private String status;
        private LocalDateTime finishTime;
        private String result;
        private LocalDateTime createTime;
        private boolean overdue;
        private long overdueDays;
    }

    @Data
    public static class FinishRequest {
        private String result;
    }

    @Data
    public static class CountersignRequest {
        private String nodeCode = "COUNTERSIGN";
        @NotBlank(message = "会签意见类型不能为空")
        private String opinionType;
        @NotBlank(message = "会签意见不能为空")
        private String opinion;
    }

    @Data
    public static class CountersignVO {
        private Long id;
        private Long caseId;
        private String nodeCode;
        private String countersignerId;
        private String countersignerName;
        private String opinionType;
        private String opinionTypeName;
        private String opinion;
        private java.time.LocalDateTime countersignTime;
        private String result;
    }

    @Data
    public static class TodoVO {
        /** TASK / COUNTERSIGN / DEADLINE */
        private String type;
        private String typeName;
        private String title;
        private Long caseId;
        private String caseNo;
        private String caseName;
        private Long targetId;
        private java.time.LocalDateTime deadline;
        private java.time.LocalDate deadlineDate;
        private String priority;
        private String status;
        private boolean overdue;
        private long overdueDays;
    }

    @Data
    public static class TodoSummary {
        private long taskCount;
        private long countersignCount;
        private long deadlineCount;
        private long overdueCount;
        private java.util.List<TodoVO> list;
    }
}
