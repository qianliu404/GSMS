package com.gsms.module.notify.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 临期案件查询：与手工推送脚本口径一致——
 * 未结案 + 案件当前节点 + 该节点期限未完成 + 截止日在今天起 N 天内。
 * 用当前节点（node_code = c.status）避免历史节点残留误报。
 */
public interface NotifyQueryMapper {

    @Select("<script>" +
            "SELECT c.case_no AS caseNo, c.case_name AS caseName, c.case_type AS caseType, " +
            "d.node_name AS nodeName, d.deadline_date AS deadlineDate, " +
            "DATEDIFF(d.deadline_date, #{today}) AS remainDays, " +
            "COALESCE(u.user_name, d.handler_id) AS handlerName, " +
            "CASE d.deadline_type WHEN 'LEGAL' THEN '法定' WHEN 'INTERNAL' THEN '内部' ELSE d.deadline_type END AS deadlineTypeName, " +
            "CASE d.status WHEN 'PENDING' THEN '待办' WHEN 'OVERDUE' THEN '已超期' WHEN 'REMINDED' THEN '已提醒' ELSE d.status END AS statusName " +
            "FROM case_main c " +
            "JOIN deadline_reminder d ON d.case_id = c.id AND d.node_code = c.status " +
            "LEFT JOIN sys_user u ON u.user_id = d.handler_id " +
            "WHERE c.deleted = 0 AND c.status &lt;&gt; 'CLOSED' " +
            "AND d.status &lt;&gt; 'DONE' " +
            "AND d.deadline_date &lt;= #{limit} " +
            "<if test='!includeOverdue'> AND d.deadline_date &gt;= #{today} </if>" +
            "ORDER BY d.deadline_date" +
            "</script>")
    List<Map<String, Object>> selectMatches(@Param("today") LocalDate today,
                                            @Param("limit") LocalDate limit,
                                            @Param("includeOverdue") boolean includeOverdue);
}
