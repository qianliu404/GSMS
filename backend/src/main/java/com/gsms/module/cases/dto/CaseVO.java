package com.gsms.module.cases.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CaseVO {

    private Long id;
    private String caseNo;
    private String caseType;
    private String caseTypeName;
    private String caseName;
    private String employeeId;
    /** 按角色脱敏后的姓名 */
    private String employeeName;
    private String departmentId;
    private String departmentName;
    /** 厂区 */
    private String plantArea;
    /** 车间班组 */
    private String workshopTeam;
    /** 小班长 / 直属主管 */
    private String leaderL1;
    /** 大班长 / 上级主管 */
    private String leaderL2;
    private LocalDate occurDate;
    private String status;
    private String statusName;
    private String ownerId;
    private String ownerName;
    private String handlerId;
    private String handlerName;
    private Integer archiveStatus;
    private LocalDate closeDate;
    private String closeType;
    private String closeTypeName;
    private Integer archivePages;
    private Integer archiveYears;
    private Integer progress;
    /** 是否存在超期期限节点 */
    private boolean overdue;
    private long overdueDays;
    /** 临近超期：存在未完成期限且距截止日不超过 3 天（已超期时该值为 false） */
    private boolean dueSoon;
    /** 距最近未完成期限截止日的天数 */
    private long daysToDeadline;
    /** 最近未完成期限的截止日期 */
    private LocalDate nearestDeadline;
    /** 当前节点的截止日期（发生日期 + 节点时限，人工调整值优先） */
    private LocalDate currentDeadline;
    /** 当前节点是否已提交开启计时；未开启不参与超期判定 */
    private boolean deadlineStarted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
