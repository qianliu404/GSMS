package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("case_main")
public class CaseMain {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseNo;
    /** INJURY / ARBITRATION / RELATED */
    private String caseType;
    private String caseName;
    private String employeeId;
    private String employeeName;
    /** AES 密文 */
    private String idCardNo;
    private String departmentId;
    private String departmentName;
    /** 厂区 */
    private String plantArea;
    /** 车间班组，如：生产一部 · 二车间甲班 */
    private String workshopTeam;
    /** 小班长 / 直属主管 */
    private String leaderL1;
    /** 大班长 / 上级主管 */
    private String leaderL2;
    private LocalDate occurDate;
    private String description;
    private String status;
    private String ownerId;
    private String handlerId;
    private LocalDate closeDate;
    /** 结案方式：TREATMENT_SETTLED/MEDIATION/AWARD/WITHDRAW/OTHER */
    private String closeType;
    private Integer archiveStatus;
    /** 卷宗页数 */
    private Integer archivePages;
    /** 保管期限（年） */
    private Integer archiveYears;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
