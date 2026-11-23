-- =============================================================
-- v1.9 期限节点收尾修复
--
-- 背景：法定时限节点（INJURY_*、ARB_*）与流程状态码（DECLARE、HEARING…）是两套编码，
-- 早期流转时没有关闭对应期限，导致案件已经走到后续节点，
-- 前面已办完的节点仍然挂着 OVERDUE，结案也被「存在超期未处理节点」拦住。
--
-- 处理：把「案件已经离开的节点」对应的期限统一置为 DONE。
-- 判定依据是 case_status_log.from_status —— 只要该状态被离开过，就说明节点已办结。
-- 幂等：重复执行不会影响已 DONE 的记录。
-- =============================================================

UPDATE deadline_reminder d
JOIN case_main c ON c.id = d.case_id
SET d.status = 'DONE',
    d.update_time = NOW()
WHERE d.status <> 'DONE'
  AND c.deleted = 0
  AND EXISTS (
    SELECT 1
    FROM case_status_log l
    WHERE l.case_id = d.case_id
      AND (
        -- 1) 手工/历史遗留的期限直接用状态码登记
        l.from_status = d.node_code
        -- 2) 工伤法定时限节点映射
        OR (c.case_type = 'INJURY' AND d.node_code = CASE l.from_status
              WHEN 'DECLARE'     THEN 'INJURY_APPLY'
              WHEN 'RECOGNITION' THEN 'INJURY_RECOGNIZE'
              WHEN 'APPRAISAL'   THEN 'INJURY_APPRAISAL'
              WHEN 'TREATMENT'   THEN 'INJURY_PAYMENT'
            END)
        -- 3) 仲裁法定时限节点映射
        OR (c.case_type = 'ARBITRATION' AND d.node_code = CASE l.from_status
              WHEN 'FILED'   THEN 'ARB_ACCEPT'
              WHEN 'HEARING' THEN 'ARB_HEARING'
              WHEN 'AWARD'   THEN 'ARB_AWARD'
            END)
      )
  );

-- 已结案/已归档案件不应再有任何未完成期限节点（历史结案案件同样收口）
UPDATE deadline_reminder d
JOIN case_main c ON c.id = d.case_id
SET d.status = 'DONE',
    d.update_time = NOW()
WHERE d.status <> 'DONE'
  AND c.deleted = 0
  AND (c.status IN ('CLOSED', 'ARCHIVED') OR c.close_date IS NOT NULL);
