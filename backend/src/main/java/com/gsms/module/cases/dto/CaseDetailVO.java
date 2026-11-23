package com.gsms.module.cases.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CaseDetailVO extends CaseVO {

    private String description;
    /** 脱敏后的身份证号 */
    private String idCardNo;
    private boolean sensitiveMasked;
    private List<MemberVO> members = new ArrayList<>();
    private List<StatusLogVO> statusLogs = new ArrayList<>();
    private InjuryExtDTO injury;
    private ArbitrationExtDTO arbitration;
    /** 当前用户可执行的动作 */
    private List<ActionVO> actions = new ArrayList<>();
    /** 可选推进目标（支持跳过中间节点，距离近的在前） */
    private List<ActionVO> forwardTargets = new ArrayList<>();
    /** 可选退回目标（最近的前驱在前） */
    private List<ActionVO> rollbackTargets = new ArrayList<>();
    private List<RelationVO> relations = new ArrayList<>();
    /** 各流程节点的截止日期（进入日 + 时限工作日），仅已到达节点返回 */
    private List<NodeDeadlineVO> nodeDeadlines = new ArrayList<>();

    @Data
    public static class MemberVO {
        private String userId;
        private String userName;
        private String memberRole;
        private String memberRoleName;
        private LocalDateTime joinTime;
    }

    @Data
    public static class StatusLogVO {
        private String fromStatus;
        private String fromStatusName;
        private String toStatus;
        private String toStatusName;
        private String action;
        private String operatorId;
        private String operatorName;
        private String opinion;
        private LocalDateTime operateTime;
    }

    /** 节点截止日期：供流程节点条展示「截止 MM-DD / 剩余 N 天」 */
    @Data
    public static class NodeDeadlineVO {
        private String statusCode;
        private String nodeName;
        private LocalDate enterDate;
        /** 本节点办理日期，默认进入节点当天，可修改 */
        private LocalDate handleDate;
        private LocalDate deadlineDate;
        /** 按「办理日期 + 节点时限」推算的应到日期，用于提示人工调整偏差 */
        private LocalDate autoDate;
        /** 是否人工调整过截止日期 */
        private boolean manual;
        /** 是否已提交开启本节点计时 */
        private boolean started;
        /** 开启计时的时间 */
        private LocalDateTime startTime;
        /** 距截止日剩余天数，负数表示已超期 */
        private Long remainDays;
        /** DONE / OVERDUE / DUE_SOON / NORMAL */
        private String state;
    }

    @Data
    public static class ActionVO {
        private String code;
        private String name;
        private boolean needOpinion;
        public ActionVO() {
        }
        public ActionVO(String code, String name, boolean needOpinion) {
            this.code = code;
            this.name = name;
            this.needOpinion = needOpinion;
        }
        public ActionVO(String code, String name) {
            this(code, name, false);
        }
    }

    @Data
    /** 争议关联案件：详情页右侧卡片展示关联案件的类型、当事人、当前阶段与关联时间 */
    public static class RelationVO {
        private Long id;
        private Long relatedCaseId;
        private String relatedCaseNo;
        private String relatedCaseName;
        private String relatedCaseType;
        private String relatedCaseTypeName;
        private String relatedEmployeeName;
        private String relatedPlantArea;
        private String relatedWorkshopTeam;
        private String relatedDepartmentName;
        private String relatedHandlerName;
        private String relatedStatusCode;
        private String relatedStatusName;
        private Integer relatedArchiveStatus;
        private String relationType;
        private String relationTypeName;
        private String relationDesc;
        private LocalDate relatedOccurDate;
        /** 兼容旧字段：当前节点名称 */
        private String relatedStatus;
        private LocalDateTime createTime;
        private String createByName;
    }
}
