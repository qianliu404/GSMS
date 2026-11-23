package com.gsms.module.deadline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.deadline.entity.DeadlineReminder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface DeadlineReminderMapper extends BaseMapper<DeadlineReminder> {

    /** 案件流转离开某节点时，把该节点的期限置为已完成 */
    @Update("UPDATE deadline_reminder SET status = 'DONE', update_time = NOW() "
            + "WHERE case_id = #{caseId} AND node_code = #{nodeCode} AND status <> 'DONE'")
    int markDoneByNode(@Param("caseId") Long caseId, @Param("nodeCode") String nodeCode);

    /** 案件结案/归档时，剩余未完成节点一并关闭 */
    @Update("UPDATE deadline_reminder SET status = 'DONE', update_time = NOW() "
            + "WHERE case_id = #{caseId} AND status <> 'DONE'")
    int markAllDone(@Param("caseId") Long caseId);

    /** 退回节点：把该节点已关闭的期限重新打开，按截止日回落为待办或超期 */
    @Update("UPDATE deadline_reminder SET status = CASE WHEN deadline_date < #{today} THEN 'OVERDUE' ELSE 'PENDING' END, "
            + "update_time = NOW() WHERE case_id = #{caseId} AND node_code = #{nodeCode} AND status = 'DONE'")
    int reopenByNode(@Param("caseId") Long caseId, @Param("nodeCode") String nodeCode,
                     @Param("today") LocalDate today);

    /** 待超期扫描：未完成的期限节点（工伤法定时限与流程状态是两套编码，不能按状态过滤） */
    @Select("SELECT d.* FROM deadline_reminder d JOIN case_main c ON c.id = d.case_id "
            + "WHERE c.deleted = 0 AND d.status IN ('PENDING', 'REMINDED') "
            + "AND d.start_time IS NOT NULL AND d.deadline_date < #{today}")
    List<DeadlineReminder> selectOverdueCandidates(@Param("today") LocalDate today);

    /** 待提醒扫描：进入各自提醒窗口的未完成节点 */
    @Select("SELECT d.* FROM deadline_reminder d JOIN case_main c ON c.id = d.case_id "
            + "WHERE c.deleted = 0 AND d.status = 'PENDING' "
            + "AND d.start_time IS NOT NULL AND d.deadline_date >= #{today} "
            + "AND d.deadline_date <= DATE_ADD(#{today}, INTERVAL GREATEST(COALESCE(d.remind_days, 7), 7) DAY)")
    List<DeadlineReminder> selectRemindCandidates(@Param("today") LocalDate today);

    /** 批量置为超期：只处理已过截止日且未完成的节点，一条 SQL 取代逐行更新 */
    @Update("UPDATE deadline_reminder d JOIN case_main c ON c.id = d.case_id "
            + "SET d.status = 'OVERDUE', d.overdue_level = IFNULL(d.overdue_level, 0) + 1, d.update_time = NOW() "
            + "WHERE c.deleted = 0 AND d.status IN ('PENDING', 'REMINDED') "
            + "AND d.start_time IS NOT NULL AND d.deadline_date < #{today}")
    int markOverdue(@Param("today") LocalDate today);

    /** 批量置为已提醒：进入各行 remind_days 提醒窗口的待提醒节点 */
    @Update("UPDATE deadline_reminder d JOIN case_main c ON c.id = d.case_id "
            + "SET d.status = 'REMINDED', d.update_time = NOW() "
            + "WHERE c.deleted = 0 AND d.status = 'PENDING' "
            + "AND d.start_time IS NOT NULL AND d.deadline_date >= #{today} "
            + "AND d.deadline_date <= DATE_ADD(#{today}, INTERVAL GREATEST(COALESCE(d.remind_days, 7), 7) DAY)")
    int markReminded(@Param("today") LocalDate today);
}
