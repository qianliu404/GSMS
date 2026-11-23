-- =====================================================================
-- 演示数据（非生产）：覆盖不同厂区、班组、认定状态、伤残等级与待遇进度，
-- 便于验证流程总览 / 工伤台账 / 归档库 / 详情页的布局与统计口径。
-- 可重复执行：先删除本脚本写入的 100+ 号段数据。
-- =====================================================================
USE case_system;

DELETE FROM case_status_log WHERE case_id BETWEEN 100 AND 199;
DELETE FROM case_member     WHERE case_id BETWEEN 100 AND 199;
DELETE FROM case_relation   WHERE case_id BETWEEN 100 AND 199 OR related_case_id BETWEEN 100 AND 199;
DELETE FROM wf_instance     WHERE case_id BETWEEN 100 AND 199;
DELETE FROM deadline_reminder WHERE case_id BETWEEN 100 AND 199;
DELETE FROM case_injury_ext      WHERE case_id BETWEEN 100 AND 199;
DELETE FROM case_arbitration_ext WHERE case_id BETWEEN 100 AND 199;
DELETE FROM case_main       WHERE id BETWEEN 100 AND 199;

-- 演示承办人（密码与 admin 相同，见部署说明）
INSERT INTO sys_user (user_id, user_name, dept_id, mobile, email, position, password_hash, status) VALUES
 ('lijing',   '李静', 3, '13800000011', 'lijing@gsms.local',   '法务主管',  '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1),
 ('chenpeng', '陈鹏', 3, '13800000012', 'chenpeng@gsms.local', '法务专员',  '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1),
 ('zhouying', '周颖', 3, '13800000013', 'zhouying@gsms.local', '法务专员',  '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1),
 ('wuqian',   '吴倩', 3, '13800000014', 'wuqian@gsms.local',   '仲裁专员',  '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1),
 ('zhengkai', '郑凯', 3, '13800000015', 'zhengkai@gsms.local', '法务专员',  '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1)
ON DUPLICATE KEY UPDATE user_name = VALUES(user_name), position = VALUES(position);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 4 FROM sys_user u
 WHERE u.user_id IN ('lijing','chenpeng','zhouying','wuqian','zhengkai')
   AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.user_id AND r.role_id = 4);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 'lijing', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = 'lijing' AND r.role_id = 2);

-- ---------- 案件主表 ----------
INSERT INTO case_main
 (id, case_no, case_type, case_name, employee_id, employee_name, department_id, department_name,
  plant_area, workshop_team, leader_l1, leader_l2, occur_date, description, status,
  owner_id, handler_id, close_date, close_type, archive_status, archive_pages, archive_years, create_by) VALUES
 (100,'GY2026110005','INJURY','李凤工伤待遇案','E1001','李凤','4','后勤保障部','华东厂区','后勤保障部 · 保洁班','赵春燕','孙立','2026-08-04','清扫车间楼梯时踏空滑倒，右腕扭伤','TREATMENT','zhouying','zhouying',NULL,NULL,0,NULL,NULL,'zhouying'),
 (101,'GY2026110006','INJURY','孙浩冲压伤案','E1002','孙浩','4','生产二部','华东厂区','生产二部 · 一车间乙班','王强','刘国栋','2026-11-11','操作冲压机时右手食指压伤，末节离断','FILED','chenpeng','chenpeng',NULL,NULL,0,NULL,NULL,'chenpeng'),
 (102,'GY2026110007','INJURY','赵敏腰部扭伤案','E1003','赵敏','4','品质部','华南厂区','品质部 · 检验组','李慧','陈明','2026-07-22','搬运检验样品时腰部扭伤，腰椎间盘突出','DISPUTE','zhouying','zhouying',NULL,NULL,0,NULL,NULL,'zhouying'),
 (103,'GY2026110008','INJURY','刘志强手部挤压伤案','E1004','刘志强','4','设备动力部','华东厂区','设备动力部 · 维修班','周德海','张伟','2026-10-29','检修传送带时右手被卷入，手掌挤压伤','INVESTIGATION','chenpeng','chenpeng',NULL,NULL,0,NULL,NULL,'chenpeng'),
 (104,'GY2026110009','INJURY','王秀兰胫骨骨折案','E1005','王秀兰','4','仓储物流部','西南厂区','仓储物流部 · 叉车组','何军','李斌','2026-10-16','叉车倒车时撞伤左腿，胫骨骨折','DECLARE','lijing','lijing',NULL,NULL,0,NULL,NULL,'lijing'),
 (105,'GY2026110010','INJURY','张建国掌骨骨折案','E1006','张建国','4','生产一部','华东厂区','生产一部 · 二车间甲班','王强','刘国栋','2026-09-12','搬运物料时被叉车撞伤左足，左手掌骨骨折','APPRAISAL','lijing','lijing',NULL,NULL,0,NULL,NULL,'lijing'),
 (106,'GY2026110011','INJURY','马涛腰椎骨折案','E1007','马涛','4','生产一部','华东厂区','生产一部 · 三车间丙班','王强','刘国栋','2026-05-18','高处作业坠落致腰椎压缩性骨折','ARCHIVED','lijing','lijing','2026-11-08','TREATMENT_SETTLED',1,128,30,'lijing'),
 (107,'GY2026110012','INJURY','周晓轻微伤案','E1008','周晓','4','生产一部','华东厂区','生产一部 · 一车间甲班','王强','刘国栋','2026-11-12','搬运时脚踝轻微扭伤，门诊处理','DRAFT','chenpeng','chenpeng',NULL,NULL,0,NULL,NULL,'chenpeng'),
 (108,'ZS2026110001','ARBITRATION','何静调岗降薪争议案','E2001','何静','2','人事行政部','总部基地','人事行政部 · 人事组','—','—','2026-11-10','调岗降薪引发争议，诉求补发工资差额 4.2 万元','FILED','wuqian','wuqian',NULL,NULL,0,NULL,NULL,'wuqian'),
 (109,'ZS2026110002','ARBITRATION','徐磊加班费争议案','E2002','徐磊','2','研发中心','总部基地','研发中心 · 结构组','—','—','2026-09-15','加班费及未休年假折算争议，诉求 8.6 万元','PERFORM','zhengkai','zhengkai',NULL,NULL,0,NULL,NULL,'zhengkai'),
 (110,'ZS2026110003','ARBITRATION','吴芳违法解除争议案','E2003','吴芳','5','财务部','华南厂区','财务部 · 成本组','—','—','2026-10-21','违法解除劳动合同，诉求赔偿金 12.8 万元','EVIDENCE','zhengkai','zhengkai',NULL,NULL,0,NULL,NULL,'zhengkai'),
 (111,'ZS2026110004','ARBITRATION','黄伟停工留薪争议案','E2004','黄伟','4','生产三部','华东厂区','生产三部 · 二车间','—','—','2026-09-28','工伤停工留薪期工资标准争议，诉求 5.4 万元','HEARING','wuqian','wuqian',NULL,NULL,0,NULL,NULL,'wuqian');

-- ---------- 工伤扩展 ----------
INSERT INTO case_injury_ext
 (case_id, injury_date, injury_place, injury_reason, apply_date, recognize_date, recognize_result,
  appraisal_date, appraisal_level, treatment_amount,
  subsidy_disability_status, subsidy_disability_amount,
  subsidy_medical_status, subsidy_medical_amount,
  subsidy_employment_status, subsidy_employment_amount, dispute_flag) VALUES
 (100,'2026-08-04','华东厂区 · 保洁班楼梯间','清扫车间楼梯时踏空滑倒，右腕扭伤','2026-08-10','2026-09-20','RECOGNIZED','2026-10-25','九级',86400,'RECEIVED',46800,'RECEIVED',16200,'RECEIVED',23400,0),
 (101,'2026-11-11','华东厂区 · 一车间乙班','操作冲压机时右手食指压伤，末节离断',NULL,NULL,'NOT_DECLARED',NULL,NULL,NULL,'NOT_APPLIED',0,'NOT_APPLIED',0,'NOT_APPLIED',0,0),
 (102,'2026-07-22','华南厂区 · 检验组','搬运检验样品时腰部扭伤，腰椎间盘突出','2026-07-30','2026-09-05','RECOGNIZED','2026-10-10','十级',96000,'RECEIVED',52000,'APPLYING',18000,'NOT_APPLIED',26000,1),
 (103,'2026-10-29','华东厂区 · 维修班','检修传送带时右手被卷入，手掌挤压伤','2026-11-02',NULL,'DECLARING',NULL,NULL,NULL,'NOT_APPLIED',0,'NOT_APPLIED',0,'NOT_APPLIED',0,0),
 (104,'2026-10-16','西南厂区 · 叉车通道','叉车倒车时撞伤左腿，胫骨骨折','2026-10-20',NULL,'DECLARING',NULL,NULL,NULL,'NOT_APPLIED',0,'NOT_APPLIED',0,'NOT_APPLIED',0,0),
 (105,'2026-09-12','华东厂区 · 二车间','搬运物料时被叉车撞伤左足，左手掌骨骨折','2026-09-18','2026-10-28','RECOGNIZED','2026-11-08','十级',96000,'NOT_APPLIED',52000,'NOT_APPLIED',18000,'NOT_APPLIED',26000,0),
 (106,'2026-05-18','华东厂区 · 三车间','高处作业坠落致腰椎压缩性骨折','2026-05-24','2026-07-02','RECOGNIZED','2026-08-15','八级',144000,'RECEIVED',78000,'RECEIVED',27000,'RECEIVED',39000,0),
 (107,'2026-11-12','华东厂区 · 一车间','搬运时脚踝轻微扭伤，门诊处理',NULL,NULL,'NOT_DECLARED',NULL,NULL,NULL,'NOT_APPLIED',0,'NOT_APPLIED',0,'NOT_APPLIED',0,0);

-- ---------- 仲裁扩展 ----------
INSERT INTO case_arbitration_ext
 (case_id, arbitration_no, apply_date, accept_date, hearing_date, result_type, award_amount, perform_status, perform_date) VALUES
 (108,'市劳人仲字〔2026〕第 118 号','2026-11-10','2026-11-12',NULL,NULL,NULL,NULL,NULL),
 (109,'市劳人仲字〔2026〕第 096 号','2026-09-15','2026-09-18','2026-10-20','MEDIATION',86000,'DONE','2026-11-05'),
 (110,'市劳人仲字〔2026〕第 103 号','2026-10-21','2026-10-24','2026-11-18',NULL,NULL,NULL,NULL),
 (111,'市劳人仲字〔2026〕第 089 号','2026-09-28','2026-10-08','2026-11-06',NULL,NULL,NULL,NULL);

-- ---------- 成员（承办人 + 负责人） ----------
INSERT INTO case_member (case_id, user_id, member_role, join_time)
SELECT c.id, c.handler_id, 'HANDLER', c.create_time FROM case_main c WHERE c.id BETWEEN 100 AND 199;
INSERT INTO case_member (case_id, user_id, member_role, join_time)
SELECT c.id, c.owner_id, 'OWNER', c.create_time FROM case_main c
 WHERE c.id BETWEEN 100 AND 199 AND c.owner_id <> c.handler_id;

-- ---------- 流程实例 ----------
INSERT INTO wf_instance (case_id, process_def_id, process_instance_id, business_key, status, start_time)
SELECT c.id, CONCAT('case-', LOWER(c.case_type)), CONCAT('demo', c.id, LPAD(FLOOR(RAND()*100000),5,'0')),
       c.case_no, CASE WHEN c.status = 'ARCHIVED' THEN 'FINISHED' ELSE 'RUNNING' END, c.create_time
  FROM case_main c WHERE c.id BETWEEN 100 AND 199;

-- ---------- 流转留痕：按主链顺序生成至当前状态的完整记录 ----------
INSERT INTO case_status_log (case_id, from_status, to_status, action, operator_id, opinion, operate_time)
WITH chain (flow_key, seq, status_code, node_name) AS (
  SELECT 'INJURY',1,'DRAFT','草稿' UNION ALL
  SELECT 'INJURY',2,'FILED','立案' UNION ALL
  SELECT 'INJURY',3,'INVESTIGATION','调查取证' UNION ALL
  SELECT 'INJURY',4,'DECLARE','申报' UNION ALL
  SELECT 'INJURY',5,'RECOGNITION','认定' UNION ALL
  SELECT 'INJURY',6,'APPRAISAL','鉴定' UNION ALL
  SELECT 'INJURY',7,'TREATMENT','待遇处理' UNION ALL
  SELECT 'INJURY',8,'DISPUTE','争议处理' UNION ALL
  SELECT 'INJURY',9,'CLOSED','结案' UNION ALL
  SELECT 'INJURY',10,'ARCHIVED','归档' UNION ALL
  SELECT 'ARBITRATION',1,'DRAFT','草稿' UNION ALL
  SELECT 'ARBITRATION',2,'FILED','立案' UNION ALL
  SELECT 'ARBITRATION',3,'EVIDENCE','证据准备' UNION ALL
  SELECT 'ARBITRATION',4,'HEARING','开庭' UNION ALL
  SELECT 'ARBITRATION',5,'AWARD','裁决/调解' UNION ALL
  SELECT 'ARBITRATION',6,'PERFORM','履行' UNION ALL
  SELECT 'ARBITRATION',7,'CLOSED','结案' UNION ALL
  SELECT 'ARBITRATION',8,'ARCHIVED','归档'
)
SELECT c.id,
       IFNULL(prev.status_code, 'NONE'),
       t.status_code,
       CASE WHEN t.seq = 1 THEN 'CREATE' WHEN t.status_code = 'ARCHIVED' THEN 'ARCHIVE' ELSE 'TRANSIT' END,
       c.handler_id,
       CASE WHEN t.seq = 1 THEN '案件建档'
            WHEN t.status_code = 'ARCHIVED' THEN '结案归档，卷宗进入档案库'
            ELSE CONCAT('完成「', t.node_name, '」节点，材料齐全转入下一节点') END,
       DATE_ADD(c.create_time, INTERVAL (t.seq - 1) * 2 DAY)
  FROM case_main c
  JOIN chain t ON t.flow_key = c.case_type
  LEFT JOIN chain prev ON prev.flow_key = t.flow_key AND prev.seq = t.seq - 1
 WHERE c.id BETWEEN 100 AND 199
   AND t.seq <= (SELECT tt.seq FROM chain tt
                  WHERE tt.flow_key = c.case_type
                    AND tt.status_code = CASE WHEN c.status = 'MEDIATION' THEN 'AWARD' ELSE c.status END);

-- ---------- 期限节点（在办案件各生成一条当前节点提醒） ----------
INSERT INTO deadline_reminder (case_id, node_code, node_name, deadline_type, deadline_date, remind_days, status, handler_id)
SELECT c.id, c.status,
       CASE c.status
         WHEN 'FILED' THEN '立案' WHEN 'INVESTIGATION' THEN '调查取证' WHEN 'DECLARE' THEN '申报'
         WHEN 'RECOGNITION' THEN '认定' WHEN 'APPRAISAL' THEN '鉴定' WHEN 'TREATMENT' THEN '待遇处理'
         WHEN 'DISPUTE' THEN '争议处理' WHEN 'EVIDENCE' THEN '证据准备' WHEN 'HEARING' THEN '开庭'
         WHEN 'AWARD' THEN '裁决/调解' WHEN 'PERFORM' THEN '履行' ELSE '办理' END,
       'LEGAL', DATE_ADD(CURDATE(), INTERVAL (c.id % 9 - 3) DAY), 3,
       CASE WHEN c.id % 9 - 3 < 0 THEN 'OVERDUE' ELSE 'PENDING' END,
       c.handler_id
  FROM case_main c
 WHERE c.id BETWEEN 100 AND 199 AND c.status NOT IN ('DRAFT','CLOSED','ARCHIVED');

-- ---------- 历史联调遗留案件的期限记录（与演示案件口径一致） ----------
INSERT INTO deadline_reminder (case_id, node_code, node_name, deadline_type, deadline_date, remind_days, status, handler_id)
SELECT c.id, c.status,
       CASE c.status WHEN 'FILED' THEN '立案' WHEN 'DECLARE' THEN '申报' ELSE '办理' END,
       'LEGAL', DATE_ADD(CURDATE(), INTERVAL (c.id % 7 - 2) DAY), 3,
       CASE WHEN c.id % 7 - 2 < 0 THEN 'OVERDUE' ELSE 'PENDING' END, c.handler_id
  FROM case_main c
 WHERE c.id BETWEEN 2 AND 5 AND c.status NOT IN ('DRAFT','CLOSED','ARCHIVED')
   AND NOT EXISTS (SELECT 1 FROM deadline_reminder d WHERE d.case_id = c.id);

-- ---------- 历史联调遗留案件的组织信息补全（避免台账出现空列） ----------
UPDATE case_main SET employee_name='陈国强', plant_area='华南厂区', workshop_team='生产二部 · 二车间丙班',
       leader_l1='王强', leader_l2='刘国栋' WHERE id = 2;
UPDATE case_main SET employee_name='刘海燕', plant_area='华东厂区', workshop_team='品质部 · 抽检组',
       leader_l1='李慧', leader_l2='陈明' WHERE id = 3;
UPDATE case_main SET employee_name='杨志明', plant_area='西南厂区', workshop_team='设备动力部 · 电气班',
       leader_l1='周德海', leader_l2='张伟' WHERE id = 4;
UPDATE case_main SET employee_name='许文静', plant_area='华东厂区', workshop_team='仓储物流部 · 库管组',
       leader_l1='何军', leader_l2='李斌' WHERE id = 5;

-- ---------- 校验 ----------
SELECT '演示案件' AS chk, case_type, COUNT(*) AS cnt FROM case_main WHERE id BETWEEN 100 AND 199 GROUP BY case_type;
SELECT '厂区覆盖' AS chk, COUNT(DISTINCT plant_area) AS areas FROM case_main WHERE id BETWEEN 100 AND 199;
SELECT '归档卷宗' AS chk, COUNT(*) AS cnt FROM case_main WHERE archive_status = 1;
