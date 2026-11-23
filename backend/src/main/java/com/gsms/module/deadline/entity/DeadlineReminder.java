package com.gsms.module.deadline.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("deadline_reminder")
public class DeadlineReminder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String nodeCode;
    private String nodeName;
    /** LEGAL 法定 / INTERNAL 内部 */
    private String deadlineType;
    private LocalDate deadlineDate;
    /**
     * 人工调整后的截止日期，为空表示按「发生日期 + 节点时限」自动推算。
     *
     * 取消人工调整时需要把该列写回 NULL，MyBatis-Plus 默认忽略 null 字段，
     * 这里显式声明 IGNORED，否则「按时限重算」永远清不掉调整值。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate manualDate;
    /**
     * 本节点办理日期：进入节点时默认当天，可修改；截止日期按该日期 + 节点时限（工作日）推算。
     * 清空时沿用旧口径（案件发生日期）以兼容历史数据。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate handleDate;
    /**
     * 节点开始计时时间：承办人点击「提交」开启当前节点后写入。
     *
     * 为空表示节点尚未开启，此时只展示截止日期，不参与超期/临近超期判定；
     * 退回重办重新进入节点时清空，需要重新提交开启。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime startTime;
    private Integer remindDays;
    /** PENDING / REMINDED / OVERDUE / DONE */
    private String status;
    private String handlerId;
    private Integer overdueLevel;
    private String overdueReason;
    private String superviseOpinion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
