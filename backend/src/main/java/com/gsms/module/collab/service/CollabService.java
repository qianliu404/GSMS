package com.gsms.module.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.service.CaseService;
import com.gsms.module.collab.dto.CollabDTO;
import com.gsms.module.collab.entity.Countersign;
import com.gsms.module.collab.entity.Task;
import com.gsms.module.collab.mapper.CountersignMapper;
import com.gsms.module.collab.mapper.TaskMapper;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.deadline.entity.DeadlineReminder;
import com.gsms.module.deadline.mapper.DeadlineReminderMapper;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollabService {

    private static final Map<String, String> OPINION_NAME = new HashMap<>();

    static {
        OPINION_NAME.put("AGREE", "同意");
        OPINION_NAME.put("DISAGREE", "不同意");
        OPINION_NAME.put("CONDITIONAL", "有条件同意");
    }

    private final TaskMapper taskMapper;
    private final CountersignMapper countersignMapper;
    private final CaseMainMapper caseMainMapper;
    private final CaseService caseService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SysUserMapper userMapper;
    private final DeadlineReminderMapper deadlineMapper;

    public CollabService(TaskMapper taskMapper, CountersignMapper countersignMapper,
                         CaseMainMapper caseMainMapper,
                         CaseService caseService, NotificationService notificationService,
                         AuditService auditService, SysUserMapper userMapper,
                         DeadlineReminderMapper deadlineMapper) {
        this.taskMapper = taskMapper;
        this.countersignMapper = countersignMapper;
        this.caseMainMapper = caseMainMapper;
        this.caseService = caseService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.deadlineMapper = deadlineMapper;
    }

    // ==================== 任务 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long caseId, CollabDTO.TaskCreateRequest req) {
        CaseMain caseMain = caseService.requireAccessible(caseId);
        assertNotArchived(caseMain);
        LoginUser current = UserContext.get();

        Task task = new Task();
        task.setCaseId(caseId);
        task.setTaskType(req.getTaskType());
        task.setAssignerId(current.getUserId());
        task.setAssigneeId(req.getAssigneeId());
        task.setDeadline(req.getDeadline());
        task.setPriority(StringUtils.hasText(req.getPriority()) ? req.getPriority() : "MEDIUM");
        task.setStatus("PENDING");
        task.setCreateTime(LocalDateTime.now());
        task.setDeleted(0);
        taskMapper.insert(task);

        notificationService.sendInner(req.getAssigneeId(), "新任务分派",
                String.format("案件【%s】分派任务：%s，截止 %s", caseMain.getCaseNo(), req.getTaskType(),
                        req.getDeadline()),
                "TASK_ASSIGN", String.valueOf(task.getId()));
        auditService.success("协同中心", "分派任务", String.valueOf(task.getId()),
                String.format("caseId=%d, assignee=%s", caseId, req.getAssigneeId()));
        return task.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishTask(Long taskId, String result) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        LoginUser current = UserContext.get();
        if (!current.getUserId().equals(task.getAssigneeId())
                && !current.hasRole("ROLE_SYS_ADMIN") && !current.hasRole("ROLE_CASE_ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅任务责任人可完成任务");
        }
        if ("DONE".equals(task.getStatus())) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "任务已完成，请勿重复提交");
        }
        task.setStatus("DONE");
        task.setFinishTime(LocalDateTime.now());
        task.setResult(result);
        taskMapper.updateById(task);

        notificationService.sendInner(task.getAssignerId(), "任务已完成",
                String.format("任务【%s】已由 %s 完成", task.getTaskType(), current.getUserName()),
                "TASK_DONE", String.valueOf(taskId));
        auditService.success("协同中心", "完成任务", String.valueOf(taskId), result);
    }

    public List<CollabDTO.TaskVO> listTasks(Long caseId) {
        caseService.requireAccessible(caseId);
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getCaseId, caseId)
                .orderByDesc(Task::getId));
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> names = loadNames(tasks.stream()
                .flatMap(t -> Arrays.stream(new String[]{t.getAssignerId(), t.getAssigneeId()}))
                .collect(Collectors.toSet()));
        List<CollabDTO.TaskVO> result = new ArrayList<>();
        for (Task task : tasks) {
            CollabDTO.TaskVO vo = new CollabDTO.TaskVO();
            vo.setId(task.getId());
            vo.setCaseId(task.getCaseId());
            vo.setTaskType(task.getTaskType());
            vo.setAssignerId(task.getAssignerId());
            vo.setAssignerName(names.get(task.getAssignerId()));
            vo.setAssigneeId(task.getAssigneeId());
            vo.setAssigneeName(names.get(task.getAssigneeId()));
            vo.setDeadline(task.getDeadline());
            vo.setPriority(task.getPriority());
            vo.setStatus(task.getStatus());
            vo.setFinishTime(task.getFinishTime());
            vo.setResult(task.getResult());
            vo.setCreateTime(task.getCreateTime());
            if ("PENDING".equals(task.getStatus()) && task.getDeadline() != null
                    && task.getDeadline().isBefore(LocalDateTime.now())) {
                vo.setOverdue(true);
                vo.setOverdueDays(ChronoUnit.DAYS.between(task.getDeadline().toLocalDate(), LocalDate.now()));
            }
            result.add(vo);
        }
        return result;
    }

    // ==================== 会签 ====================

    /** 发起会签：为该案件全部会签人生成待会签记录 */
    @Transactional(rollbackFor = Exception.class)
    public int initiateCountersign(Long caseId, String nodeCode) {
        CaseMain caseMain = caseService.requireAccessible(caseId);
        assertNotArchived(caseMain);
        List<String> countersigners = caseService.countersignerIds(caseId);
        if (countersigners.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该案件尚未配置会签人");
        }
        String node = StringUtils.hasText(nodeCode) ? nodeCode : "COUNTERSIGN";
        int created = 0;
        for (String userId : countersigners) {
            Long exists = countersignMapper.selectCount(new LambdaQueryWrapper<Countersign>()
                    .eq(Countersign::getCaseId, caseId)
                    .eq(Countersign::getNodeCode, node)
                    .eq(Countersign::getCountersignerId, userId)
                    .eq(Countersign::getResult, "PENDING"));
            if (exists != null && exists > 0) {
                continue;
            }
            Countersign entity = new Countersign();
            entity.setCaseId(caseId);
            entity.setNodeCode(node);
            entity.setCountersignerId(userId);
            entity.setResult("PENDING");
            entity.setCreateTime(LocalDateTime.now());
            countersignMapper.insert(entity);
            notificationService.sendInner(userId, "待会签",
                    String.format("案件【%s】发起会签，请及时处理", caseMain.getCaseNo()),
                    "COUNTERSIGN_TODO", String.valueOf(entity.getId()));
            created++;
        }
        auditService.success("协同中心", "发起会签", String.valueOf(caseId), "node=" + node + ", created=" + created);
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitCountersign(Long caseId, CollabDTO.CountersignRequest req) {
        caseService.requireAccessible(caseId);
        LoginUser current = UserContext.get();
        Countersign entity = countersignMapper.selectOne(new LambdaQueryWrapper<Countersign>()
                .eq(Countersign::getCaseId, caseId)
                .eq(Countersign::getCountersignerId, current.getUserId())
                .eq(Countersign::getResult, "PENDING")
                .orderByDesc(Countersign::getId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "没有待处理的会签任务");
        }
        entity.setOpinionType(req.getOpinionType());
        entity.setOpinion(req.getOpinion());
        entity.setCountersignTime(LocalDateTime.now());
        entity.setResult("DISAGREE".equals(req.getOpinionType()) ? "REJECT" : "PASS");
        countersignMapper.updateById(entity);
        auditService.success("协同中心", "提交会签", String.valueOf(entity.getId()),
                req.getOpinionType() + ":" + req.getOpinion());
    }

    public List<CollabDTO.CountersignVO> listCountersigns(Long caseId) {
        caseService.requireAccessible(caseId);
        List<Countersign> list = countersignMapper.selectList(new LambdaQueryWrapper<Countersign>()
                .eq(Countersign::getCaseId, caseId)
                .orderByDesc(Countersign::getId));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> names = loadNames(list.stream()
                .map(Countersign::getCountersignerId).collect(Collectors.toSet()));
        List<CollabDTO.CountersignVO> result = new ArrayList<>();
        for (Countersign item : list) {
            CollabDTO.CountersignVO vo = new CollabDTO.CountersignVO();
            vo.setId(item.getId());
            vo.setCaseId(item.getCaseId());
            vo.setNodeCode(item.getNodeCode());
            vo.setCountersignerId(item.getCountersignerId());
            vo.setCountersignerName(names.get(item.getCountersignerId()));
            vo.setOpinionType(item.getOpinionType());
            vo.setOpinionTypeName(item.getOpinionType() == null ? null
                    : OPINION_NAME.getOrDefault(item.getOpinionType(), item.getOpinionType()));
            vo.setOpinion(item.getOpinion());
            vo.setCountersignTime(item.getCountersignTime());
            vo.setResult(item.getResult());
            result.add(vo);
        }
        return result;
    }

    // ==================== 我的待办 ====================

    public CollabDTO.TodoSummary myTodos() {
        LoginUser current = UserContext.get();
        String me = current.getUserId();
        List<CollabDTO.TodoVO> todos = new ArrayList<>();

        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, me)
                .in(Task::getStatus, "PENDING", "OVERDUE")
                .orderByAsc(Task::getDeadline));
        List<Countersign> countersigns = countersignMapper.selectList(new LambdaQueryWrapper<Countersign>()
                .eq(Countersign::getCountersignerId, me)
                .eq(Countersign::getResult, "PENDING")
                .orderByAsc(Countersign::getId));
        List<DeadlineReminder> deadlines = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getHandlerId, me)
                .in(DeadlineReminder::getStatus, "PENDING", "REMINDED", "OVERDUE")
                .orderByAsc(DeadlineReminder::getDeadlineDate));

        Set<Long> caseIds = new HashSet<>();
        tasks.forEach(t -> caseIds.add(t.getCaseId()));
        countersigns.forEach(c -> caseIds.add(c.getCaseId()));
        deadlines.forEach(d -> caseIds.add(d.getCaseId()));
        Map<Long, CaseMain> caseMap = caseIds.isEmpty() ? Collections.emptyMap()
                : caseMainMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseMain::getId, c -> c, (a, b) -> a));

        for (Task task : tasks) {
            CollabDTO.TodoVO vo = new CollabDTO.TodoVO();
            vo.setType("TASK");
            vo.setTypeName("待办理");
            vo.setTitle(task.getTaskType());
            vo.setTargetId(task.getId());
            vo.setDeadline(task.getDeadline());
            vo.setPriority(task.getPriority());
            vo.setStatus(task.getStatus());
            fillCase(vo, caseMap.get(task.getCaseId()));
            if (task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now())) {
                vo.setOverdue(true);
                vo.setOverdueDays(ChronoUnit.DAYS.between(task.getDeadline().toLocalDate(), LocalDate.now()));
            }
            todos.add(vo);
        }

        for (Countersign item : countersigns) {
            CollabDTO.TodoVO vo = new CollabDTO.TodoVO();
            vo.setType("COUNTERSIGN");
            vo.setTypeName("待会签");
            vo.setTitle("会签节点：" + item.getNodeCode());
            vo.setTargetId(item.getId());
            vo.setStatus(item.getResult());
            fillCase(vo, caseMap.get(item.getCaseId()));
            todos.add(vo);
        }

        for (DeadlineReminder item : deadlines) {
            CollabDTO.TodoVO vo = new CollabDTO.TodoVO();
            vo.setType("DEADLINE");
            vo.setTypeName("期限提醒");
            vo.setTitle(item.getNodeName());
            vo.setTargetId(item.getId());
            vo.setDeadlineDate(item.getDeadlineDate());
            vo.setStatus(item.getStatus());
            fillCase(vo, caseMap.get(item.getCaseId()));
            if (item.getDeadlineDate() != null && item.getDeadlineDate().isBefore(LocalDate.now())) {
                vo.setOverdue(true);
                vo.setOverdueDays(ChronoUnit.DAYS.between(item.getDeadlineDate(), LocalDate.now()));
            }
            todos.add(vo);
        }

        todos.sort((a, b) -> Boolean.compare(b.isOverdue(), a.isOverdue()));

        CollabDTO.TodoSummary summary = new CollabDTO.TodoSummary();
        summary.setTaskCount(tasks.size());
        summary.setCountersignCount(countersigns.size());
        summary.setDeadlineCount(deadlines.size());
        summary.setOverdueCount(todos.stream().filter(CollabDTO.TodoVO::isOverdue).count());
        summary.setList(todos);
        return summary;
    }

    private void fillCase(CollabDTO.TodoVO vo, CaseMain caseMain) {
        if (caseMain != null) {
            vo.setCaseId(caseMain.getId());
            vo.setCaseNo(caseMain.getCaseNo());
            vo.setCaseName(caseMain.getCaseName());
        }
    }

    private void assertNotArchived(CaseMain caseMain) {
        if (caseMain.getArchiveStatus() != null && caseMain.getArchiveStatus() == 1) {
            throw new BizException(ErrorCode.ALREADY_ARCHIVED);
        }
    }

    private Map<String, String> loadNames(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUserId, userIds)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));
    }
}
