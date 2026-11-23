package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.*;
import com.gsms.module.cases.dto.*;
import com.gsms.module.cases.entity.*;
import com.gsms.module.cases.mapper.*;
import com.gsms.module.deadline.entity.DeadlineReminder;
import com.gsms.module.deadline.mapper.DeadlineReminderMapper;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.deadline.service.DeadlineService;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import com.gsms.module.system.service.DeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CaseService {

    private static final String ROLE_SYS_ADMIN = "ROLE_SYS_ADMIN";
    private static final String ROLE_CASE_ADMIN = "ROLE_CASE_ADMIN";
    private static final String ROLE_DEPT_HEAD = "ROLE_DEPT_HEAD";
    private static final String ROLE_AUDITOR = "ROLE_AUDITOR";

    private static final Map<String, String> NO_PREFIX = new HashMap<>();

    static {
        NO_PREFIX.put(CaseStateMachine.TYPE_INJURY, "GY");
        NO_PREFIX.put(CaseStateMachine.TYPE_ARBITRATION, "ZS");
        NO_PREFIX.put(CaseStateMachine.TYPE_RELATED, "GL");
    }

    private final CaseMainMapper caseMainMapper;
    private final CaseInjuryExtMapper injuryExtMapper;
    private final CaseArbitrationExtMapper arbitrationExtMapper;
    private final CaseMemberMapper caseMemberMapper;
    private final CaseStatusLogMapper statusLogMapper;
    private final CaseRelationMapper relationMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final DeadlineReminderMapper deadlineMapper;
    private final SysUserMapper userMapper;
    private final DeptService deptService;
    private final CaseStateMachine stateMachine;
    private final WorkflowFlowService flowService;
    private final CryptoUtil cryptoUtil;
    private final DeadlineService deadlineService;
    private final AuditService auditService;

    public CaseService(CaseMainMapper caseMainMapper, CaseInjuryExtMapper injuryExtMapper,
                       CaseArbitrationExtMapper arbitrationExtMapper, CaseMemberMapper caseMemberMapper,
                       CaseStatusLogMapper statusLogMapper, CaseRelationMapper relationMapper,
                       WfInstanceMapper wfInstanceMapper, DeadlineReminderMapper deadlineMapper,
                       SysUserMapper userMapper, DeptService deptService,
                       CaseStateMachine stateMachine, WorkflowFlowService flowService, CryptoUtil cryptoUtil,
                       DeadlineService deadlineService, AuditService auditService) {
        this.caseMainMapper = caseMainMapper;
        this.injuryExtMapper = injuryExtMapper;
        this.arbitrationExtMapper = arbitrationExtMapper;
        this.caseMemberMapper = caseMemberMapper;
        this.statusLogMapper = statusLogMapper;
        this.relationMapper = relationMapper;
        this.wfInstanceMapper = wfInstanceMapper;
        this.deadlineMapper = deadlineMapper;
        this.userMapper = userMapper;
        this.deptService = deptService;
        this.stateMachine = stateMachine;
        this.flowService = flowService;
        this.cryptoUtil = cryptoUtil;
        this.deadlineService = deadlineService;
        this.auditService = auditService;
    }

    // ==================== 建档 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(CaseCreateRequest req) {
        if (!stateMachine.isSupportedType(req.getCaseType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的案件类型: " + req.getCaseType());
        }
        // 关联争议不再作为独立案件类型建档：改为「争议关联」把两件已有案件关联起来
        if (CaseStateMachine.TYPE_RELATED.equals(req.getCaseType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "关联争议已改为案件关联管理，请按工伤或劳动仲裁类型建档，再在「争议关联」中关联");
        }
        LoginUser current = UserContext.get();
        String ownerId = StringUtils.hasText(req.getOwnerId()) ? req.getOwnerId() : req.getHandlerId();

        // 新建时选中了关联案件：先校验存在性，再随建档一起写入关联
        CaseMain relatedCase = resolveRelatedCase(req.getRelatedCaseNo());

        CaseMain entity = new CaseMain();
        entity.setCaseNo(resolveCaseNo(req.getCaseType(), req.getCaseNo()));
        entity.setCaseType(req.getCaseType());
        entity.setCaseName(req.getCaseName());
        entity.setEmployeeId(req.getEmployeeId());
        entity.setEmployeeName(req.getEmployeeName());
        entity.setIdCardNo(cryptoUtil.encrypt(req.getIdCardNo()));
        String[] dept = resolveDepartment(req.getDepartmentId(), req.getDepartmentName(), req.getHandlerId());
        entity.setDepartmentId(dept[0]);
        entity.setDepartmentName(dept[1]);
        entity.setPlantArea(req.getPlantArea());
        entity.setWorkshopTeam(req.getWorkshopTeam());
        entity.setLeaderL1(req.getLeaderL1());
        entity.setLeaderL2(req.getLeaderL2());
        entity.setOccurDate(req.getOccurDate());
        entity.setDescription(req.getDescription());
        entity.setStatus(CaseStateMachine.DRAFT);
        entity.setOwnerId(ownerId);
        entity.setHandlerId(req.getHandlerId());
        entity.setArchiveStatus(0);
        entity.setCreateBy(current.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
        caseMainMapper.insert(entity);

        saveExt(entity.getId(), req);
        saveMembers(entity.getId(), ownerId, req);
        logStatus(entity.getId(), null, CaseStateMachine.DRAFT, "CREATE", "案件建档");
        if (relatedCase != null) {
            linkCase(entity, relatedCase, req.getRelationDesc());
        }
        startWorkflow(entity);
        // 建档即按案件类型生成法定/内部期限节点
        deadlineService.initForCase(entity);

        if (req.isSubmit()) {
            submit(entity.getId(), null);
        }
        log.info("案件创建成功: id={}, no={}", entity.getId(), entity.getCaseNo());
        return entity.getId();
    }

    /**
     * 历史案件导入：沿用历史案号与当前节点，只落一条 IMPORT 办理记录。
     *
     * 不追补中间节点的办理日志（没有真实记录），流程图会把这些节点显示为「已跳过」。
     * 期限记录按当前节点生成但保持「未开启」，导入不会立刻产生一堆超期告警。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long importCase(CaseImportRow row) {
        LoginUser current = UserContext.get();
        CaseMain entity = new CaseMain();
        entity.setCaseNo(row.getCaseNo());
        entity.setCaseType(row.getCaseType());
        entity.setCaseName(row.getEmployeeName()
                + (CaseStateMachine.TYPE_INJURY.equals(row.getCaseType()) ? "工伤案" : "劳动争议案"));
        entity.setEmployeeName(row.getEmployeeName());
        String[] dept = resolveDepartment(null, null, row.getHandlerId());
        entity.setDepartmentId(dept[0]);
        entity.setDepartmentName(dept[1]);
        entity.setPlantArea(row.getPlantArea());
        entity.setWorkshopTeam(row.getWorkshopTeam());
        entity.setLeaderL1(row.getLeaderL1());
        entity.setLeaderL2(row.getLeaderL2());
        entity.setOccurDate(row.getOccurDate());
        entity.setDescription(row.getDescription());
        entity.setStatus(row.getStatus());
        entity.setOwnerId(row.getHandlerId());
        entity.setHandlerId(row.getHandlerId());
        entity.setArchiveStatus(row.isArchiveFlag() ? 1 : 0);
        entity.setCloseDate(row.getCloseDate());
        entity.setCloseType(row.getCloseType());
        entity.setCreateBy(current.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
        caseMainMapper.insert(entity);

        importExt(entity.getId(), row);
        addMember(entity.getId(), row.getHandlerId(), "OWNER");
        logStatus(entity.getId(), null, row.getStatus(), "IMPORT", "历史案件导入");
        startWorkflow(entity);
        // 已结案/归档的历史案件不再生成期限节点，避免在期限中心留下无意义的待办
        if (!row.isArchiveFlag() && !CaseStateMachine.CLOSED.equals(row.getStatus())) {
            deadlineService.enterNode(entity, row.getStatus());
        }
        log.info("历史案件导入: id={}, no={}, status={}", entity.getId(), entity.getCaseNo(), entity.getStatus());
        return entity.getId();
    }

    private void importExt(Long caseId, CaseImportRow row) {
        if (CaseStateMachine.TYPE_INJURY.equals(row.getCaseType())) {
            CaseInjuryExt ext = new CaseInjuryExt();
            ext.setCaseId(caseId);
            ext.setInjuryDate(row.getOccurDate());
            ext.setInjuryPlace("-");
            ext.setInjuryReason(StringUtils.hasText(row.getInjuryReason()) ? row.getInjuryReason() : "-");
            ext.setRecognizeResult(row.getRecognizeCode());
            ext.setAppraisalLevel(row.getAppraisalLevel());
            ext.setRestDays(row.getRestDays());
            ext.setSubsidyDisabilityStatus(subsidyStatusOf(row.getSubsidyDisabilityAmount()));
            ext.setSubsidyDisabilityAmount(defaultAmount(row.getSubsidyDisabilityAmount()));
            ext.setSubsidyMedicalStatus(subsidyStatusOf(row.getSubsidyMedicalAmount()));
            ext.setSubsidyMedicalAmount(defaultAmount(row.getSubsidyMedicalAmount()));
            ext.setSubsidyEmploymentStatus(subsidyStatusOf(row.getSubsidyEmploymentAmount()));
            ext.setSubsidyEmploymentAmount(defaultAmount(row.getSubsidyEmploymentAmount()));
            ext.setDisputeFlag(0);
            injuryExtMapper.insert(ext);
            return;
        }
        CaseArbitrationExt ext = new CaseArbitrationExt();
        ext.setCaseId(caseId);
        ext.setArbitrationNo(row.getArbitrationNo());
        // 申请日期为必填列，历史数据缺失时以发生日期兜底
        ext.setApplyDate(row.getOccurDate());
        ext.setHearingDate(row.getHearingDate());
        ext.setResultType(row.getResultCode());
        ext.setAwardAmount(row.getAwardAmount());
        ext.setPerformStatus(row.getPerformCode());
        ext.setPerformAmount(row.getPerformAmount());
        arbitrationExtMapper.insert(ext);
    }

    /** 有金额视为已到账，没填金额按未申领 */
    private String subsidyStatusOf(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? "RECEIVED" : "NOT_APPLIED";
    }

    private void saveExt(Long caseId, CaseCreateRequest req) {
        if (CaseStateMachine.TYPE_INJURY.equals(req.getCaseType()) && req.getInjury() != null) {
            InjuryExtDTO dto = req.getInjury();
            CaseInjuryExt ext = new CaseInjuryExt();
            ext.setCaseId(caseId);
            ext.setInjuryDate(dto.getInjuryDate() != null ? dto.getInjuryDate() : req.getOccurDate());
            ext.setInjuryPlace(dto.getInjuryPlace());
            ext.setInjuryReason(StringUtils.hasText(dto.getInjuryReason()) ? dto.getInjuryReason() : req.getDescription());
            ext.setApplyDate(dto.getApplyDate());
            ext.setRecognizeDate(dto.getRecognizeDate());
            ext.setRecognizeResult(dto.getRecognizeResult());
            ext.setAppraisalDate(dto.getAppraisalDate());
            ext.setAppraisalLevel(dto.getAppraisalLevel());
            ext.setRestDays(dto.getRestDays());
            ext.setTreatmentAmount(dto.getTreatmentAmount());
            ext.setSubsidyDisabilityStatus(defaultSubsidyStatus(dto.getSubsidyDisabilityStatus()));
            ext.setSubsidyDisabilityAmount(defaultAmount(dto.getSubsidyDisabilityAmount()));
            ext.setSubsidyMedicalStatus(defaultSubsidyStatus(dto.getSubsidyMedicalStatus()));
            ext.setSubsidyMedicalAmount(defaultAmount(dto.getSubsidyMedicalAmount()));
            ext.setSubsidyEmploymentStatus(defaultSubsidyStatus(dto.getSubsidyEmploymentStatus()));
            ext.setSubsidyEmploymentAmount(defaultAmount(dto.getSubsidyEmploymentAmount()));
            ext.setDisputeFlag(dto.getDisputeFlag() == null ? 0 : dto.getDisputeFlag());
            injuryExtMapper.insert(ext);
        } else if (CaseStateMachine.TYPE_ARBITRATION.equals(req.getCaseType()) && req.getArbitration() != null) {
            arbitrationExtMapper.insert(toArbitrationExt(caseId, req.getArbitration()));
        }
    }

    private String defaultSubsidyStatus(String status) {
        return StringUtils.hasText(status) ? status : "NOT_APPLIED";
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private CaseArbitrationExt toArbitrationExt(Long caseId, ArbitrationExtDTO dto) {        CaseArbitrationExt ext = new CaseArbitrationExt();
        ext.setCaseId(caseId);
        ext.setArbitrationNo(dto.getArbitrationNo());
        ext.setApplyDate(dto.getApplyDate() != null ? dto.getApplyDate() : LocalDate.now());
        ext.setAcceptDate(dto.getAcceptDate());
        ext.setHearingDate(dto.getHearingDate());
        ext.setResultType(dto.getResultType());
        ext.setAwardAmount(dto.getAwardAmount());
        ext.setPerformStatus(dto.getPerformStatus());
        ext.setPerformDate(dto.getPerformDate());
        ext.setPerformAmount(dto.getPerformAmount());
        return ext;
    }

    private void saveMembers(Long caseId, String ownerId, CaseCreateRequest req) {
        addMember(caseId, ownerId, "OWNER");
        // 负责人与承办人为同一人时只保留一条，避免成员列表重复展示
        if (!Objects.equals(ownerId, req.getHandlerId())) {
            addMember(caseId, req.getHandlerId(), "HANDLER");
        }
        addMembers(caseId, req.getCoHandlerIds(), "CO_HANDLER");
        addMembers(caseId, req.getCountersignerIds(), "COUNTERSIGN");
        addMembers(caseId, req.getReaderIds(), "READER");
    }

    private void addMembers(Long caseId, List<String> userIds, String role) {
        if (userIds == null) {
            return;
        }
        for (String userId : userIds) {
            addMember(caseId, userId, role);
        }
    }

    private void addMember(Long caseId, String userId, String role) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        Long exists = caseMemberMapper.selectCount(new LambdaQueryWrapper<CaseMember>()
                .eq(CaseMember::getCaseId, caseId)
                .eq(CaseMember::getUserId, userId)
                .eq(CaseMember::getMemberRole, role));
        if (exists != null && exists > 0) {
            return;
        }
        CaseMember member = new CaseMember();
        member.setCaseId(caseId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setJoinTime(LocalDateTime.now());
        caseMemberMapper.insert(member);
    }

    private void startWorkflow(CaseMain entity) {
        WfInstance wf = new WfInstance();
        wf.setCaseId(entity.getId());
        wf.setProcessDefId("case-" + entity.getCaseType().toLowerCase());
        wf.setProcessInstanceId(UUID.randomUUID().toString().replace("-", ""));
        wf.setBusinessKey(entity.getCaseNo());
        wf.setStatus("RUNNING");
        wf.setStartTime(LocalDateTime.now());
        wfInstanceMapper.insert(wf);
    }

    /** 关联案件按案号定位；留空返回 null */
    private CaseMain resolveRelatedCase(String relatedCaseNo) {
        if (!StringUtils.hasText(relatedCaseNo)) {
            return null;
        }
        CaseMain related = caseMainMapper.selectOne(new LambdaQueryWrapper<CaseMain>()
                .eq(CaseMain::getCaseNo, relatedCaseNo.trim()));
        if (related == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "关联案件不存在: " + relatedCaseNo);
        }
        return related;
    }

    /** 新建案件时建立关联：统一先建档的一方为主案，与争议关联页的展示口径一致 */
    private void linkCase(CaseMain created, CaseMain related, String relationDesc) {
        CaseMain main = created.getId() < related.getId() ? created : related;
        CaseMain sub = main == created ? related : created;
        CaseRelation relation = new CaseRelation();
        relation.setCaseId(main.getId());
        relation.setRelatedCaseId(sub.getId());
        relation.setRelationType("RELATED");
        relation.setRelationDesc(StringUtils.hasText(relationDesc)
                ? relationDesc.trim() : "新建案件时建立关联");
        relation.setCreateBy(UserContext.userId());
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleted(0);
        relationMapper.insert(relation);
    }

    /** 当前用户是否可查看该案件：关联列表按此过滤，避免越权看到别部门案件 */
    public boolean canViewCase(CaseMain entity) {
        try {
            assertCanView(entity);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /** 选择器单次返回上限：再多下拉也滚不完，靠关键字收窄更实用 */
    private static final int MAX_OPTION_ROWS = 50;

    /**
     * 案件选择项：新建案件选中关联案件后回填基本信息，也用于争议关联页选案件。
     *
     * 大数据量下的取数策略：
     * 1. 案号用前缀匹配（可命中 uk_case_no），姓名/案件名称用包含匹配，避免整表 LIKE '%kw%'；
     * 2. 前缀没命中时退化为案号包含匹配（用户可能只记得流水号）；
     * 3. 只 select 必要列，不经过详情/列表的期限与成员装配。
     */
    public List<CaseOptionVO> options(String keyword, Integer limit) {
        int max = (limit == null || limit < 1) ? 20 : Math.min(limit, MAX_OPTION_ROWS);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        // 有关键字时多取一倍用于精确匹配置顶，最后再裁剪；无关键字直接取上限
        int fetch = kw == null ? max : Math.min(max * 2, MAX_OPTION_ROWS * 2);
        List<CaseMain> records = queryOptions(kw, fetch, false);
        if (records.isEmpty() && kw != null && kw.length() >= 4) {
            records = queryOptions(kw, fetch, true);
        }
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        if (kw != null) {
            // 精确案号置顶，其次是同前缀，最后是姓名/名称命中的结果（保持最近更新时间倒序）
            String upper = kw.toUpperCase();
            records.sort(Comparator.comparingInt(c -> {
                String no = c.getCaseNo() == null ? "" : c.getCaseNo().toUpperCase();
                if (no.equals(upper)) {
                    return 0;
                }
                return no.startsWith(upper) ? 1 : 2;
            }));
            if (records.size() > max) {
                records = new ArrayList<>(records.subList(0, max));
            }
        }

        Set<String> handlerIds = records.stream().map(CaseMain::getHandlerId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, String> handlerNames = handlerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, handlerIds))
                .stream().collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        List<CaseOptionVO> result = new ArrayList<>(records.size());
        for (CaseMain entity : records) {
            CaseOptionVO vo = new CaseOptionVO();
            vo.setId(entity.getId());
            vo.setCaseNo(entity.getCaseNo());
            vo.setCaseType(entity.getCaseType());
            vo.setCaseTypeName(CaseDict.typeName(entity.getCaseType()));
            vo.setStatus(entity.getStatus());
            vo.setStatusName(CaseDict.statusName(entity.getStatus()));
            vo.setEmployeeName(entity.getEmployeeName());
            vo.setPlantArea(entity.getPlantArea());
            vo.setWorkshopTeam(entity.getWorkshopTeam());
            vo.setLeaderL1(entity.getLeaderL1());
            vo.setLeaderL2(entity.getLeaderL2());
            vo.setHandlerId(entity.getHandlerId());
            vo.setHandlerName(handlerNames.getOrDefault(entity.getHandlerId(), entity.getHandlerId()));
            vo.setDepartmentId(entity.getDepartmentId());
            vo.setDepartmentName(entity.getDepartmentName());
            vo.setOccurDate(entity.getOccurDate());
            result.add(vo);
        }
        return result;
    }

    private List<CaseMain> queryOptions(String keyword, int fetch, boolean containsCaseNo) {
        LambdaQueryWrapper<CaseMain> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(CaseMain::getId, CaseMain::getCaseNo, CaseMain::getCaseType, CaseMain::getStatus,
                CaseMain::getEmployeeName, CaseMain::getPlantArea, CaseMain::getWorkshopTeam,
                CaseMain::getLeaderL1, CaseMain::getLeaderL2, CaseMain::getHandlerId,
                CaseMain::getDepartmentId, CaseMain::getDepartmentName, CaseMain::getOccurDate);
        if (keyword != null) {
            // 前缀匹配可走 uk_case_no；兜底时才用包含匹配（只记得流水号的情况）
            wrapper.and(w -> (containsCaseNo
                            ? w.like(CaseMain::getCaseNo, keyword)
                            : w.likeRight(CaseMain::getCaseNo, keyword))
                    .or().like(CaseMain::getEmployeeName, keyword)
                    .or().like(CaseMain::getCaseName, keyword));
        }
        applyDataScope(wrapper);
        // 多取一倍用于精确匹配置顶后再裁剪；上限来自服务端常量，非用户输入
        wrapper.orderByDesc(CaseMain::getUpdateTime).last("LIMIT " + fetch);
        return caseMainMapper.selectList(wrapper);
    }

    /**
     * 案号取值：立案时手工填写的优先，留空则按规则自动编号。
     *
     * 自定义案号只做长度与唯一性校验，不强制套用 GY/ZS/GL 前缀 ——
     * 历史卷宗、仲裁案号常是「（2025）粤0305民初1234号」这类格式。
     */
    private String resolveCaseNo(String caseType, String custom) {
        if (!StringUtils.hasText(custom)) {
            return generateCaseNo(caseType);
        }
        String no = custom.trim().replaceAll("\\s+", "");
        if (no.isEmpty()) {
            return generateCaseNo(caseType);
        }
        if (no.length() > 32) {
            throw new BizException(ErrorCode.PARAM_INVALID, "案号不能超过 32 个字符");
        }
        // 回收站里的案件同样占用 uk_case_no，必须一起排除
        if (caseMainMapper.countByCaseNo(no) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "案号「" + no + "」已存在，请换一个");
        }
        return no;
    }

    /** 编号规则：GY/ZS/GL + yyyyMM + 4 位流水 */
    private String generateCaseNo(String caseType) {
        String prefix = NO_PREFIX.get(caseType)
                + DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
        int seq = caseMainMapper.maxSeqByPrefix(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    /**
     * 部门取值：请求显式指定 → 承办人所属部门 → 建档人所属部门。
     * 表单已不填部门，靠承办人带出，保证台账归属与部门数据权限仍然成立。
     * @return {部门ID, 部门名称}
     */
    private String[] resolveDepartment(String departmentId, String departmentName, String handlerId) {
        if (StringUtils.hasText(departmentId)) {
            return new String[]{departmentId, resolveDeptName(departmentId, departmentName)};
        }
        // 注意：sys_user 主键是自增 id，业务主键是 user_id，不能按 id 查
        SysUser handler = StringUtils.hasText(handlerId)
                ? userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, handlerId))
                : null;
        Long deptId = handler != null && handler.getDeptId() != null
                ? handler.getDeptId()
                : UserContext.get().getDeptId();
        if (deptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无法确定案件所属部门，请先为承办人设置所属部门");
        }
        String id = String.valueOf(deptId);
        return new String[]{id, resolveDeptName(id, null)};
    }

    private String resolveDeptName(String departmentId, String departmentName) {
        if (StringUtils.hasText(departmentName)) {
            return departmentName;
        }
        try {
            Long deptId = Long.valueOf(departmentId);
            return deptService.listAll().stream()
                    .filter(d -> deptId.equals(d.getId()))
                    .map(d -> d.getDeptName())
                    .findFirst().orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 更新 ====================

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CaseCreateRequest req) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        // 已结案/已归档的案件不再允许改档案；流转中的案件允许修正基础信息
        // （承办人、班组、仲裁案号等在办理过程中确实会变，锁死草稿状态无法维护）
        String status = entity.getStatus();
        if (CaseStateMachine.CLOSED.equals(status) || CaseStateMachine.ARCHIVED.equals(status)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "已结案或已归档的案件不可修改");
        }
        entity.setCaseType(req.getCaseType());
        entity.setCaseName(req.getCaseName());
        entity.setEmployeeId(req.getEmployeeId());
        entity.setEmployeeName(req.getEmployeeName());
        if (StringUtils.hasText(req.getIdCardNo())) {
            entity.setIdCardNo(cryptoUtil.encrypt(req.getIdCardNo()));
        }
        if (StringUtils.hasText(req.getDepartmentId())) {
            String[] dept = resolveDepartment(req.getDepartmentId(), req.getDepartmentName(), req.getHandlerId());
            entity.setDepartmentId(dept[0]);
            entity.setDepartmentName(dept[1]);
        }
        entity.setPlantArea(req.getPlantArea());
        entity.setWorkshopTeam(req.getWorkshopTeam());
        entity.setLeaderL1(req.getLeaderL1());
        entity.setLeaderL2(req.getLeaderL2());
        entity.setOccurDate(req.getOccurDate());
        entity.setDescription(req.getDescription());
        entity.setHandlerId(req.getHandlerId());
        entity.setOwnerId(StringUtils.hasText(req.getOwnerId()) ? req.getOwnerId() : req.getHandlerId());
        entity.setUpdateTime(LocalDateTime.now());
        caseMainMapper.updateById(entity);

        injuryExtMapper.deleteById(id);
        arbitrationExtMapper.deleteById(id);
        saveExt(id, req);
    }

    // ==================== 查询 ====================

    public PageResult<CaseVO> page(CaseQuery query) {
        LambdaQueryWrapper<CaseMain> wrapper = new LambdaQueryWrapper<>();
        applyFilters(wrapper, query);
        applyDataScope(wrapper);
        wrapper.orderByDesc(CaseMain::getUpdateTime);

        Page<CaseMain> page = caseMainMapper.selectPage(query.toPage(), wrapper);
        List<CaseVO> list = convertToVOs(page.getRecords());
        return PageResult.of(page, list);
    }

    /** 单次导出上限，避免一次性把过多数据读入内存 */
    public static final int MAX_EXPORT_ROWS = 5000;

    /**
     * 导出取数：一次查询取到上限条数。
     * 原实现按 100 条/页循环调用 page()，每页都会重跑一次全量 count(*)，
     * 导出 5000 条等于 50 次多余统计；且每页都要重复装载用户/期限映射。
     */
    public List<CaseVO> listForExport(CaseQuery query, int maxRows) {
        LambdaQueryWrapper<CaseMain> wrapper = new LambdaQueryWrapper<>();
        applyFilters(wrapper, query);
        applyDataScope(wrapper);
        wrapper.orderByDesc(CaseMain::getUpdateTime);
        // maxRows 来自服务端常量，非用户输入
        wrapper.last("LIMIT " + Math.max(1, maxRows));
        return convertToVOs(caseMainMapper.selectList(wrapper));
    }

    private void applyFilters(LambdaQueryWrapper<CaseMain> wrapper, CaseQuery query) {
        wrapper.eq(StringUtils.hasText(query.getCaseType()), CaseMain::getCaseType, query.getCaseType())
                .eq(StringUtils.hasText(query.getStatus()), CaseMain::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDepartmentId()), CaseMain::getDepartmentId, query.getDepartmentId())
                .eq(StringUtils.hasText(query.getHandlerId()), CaseMain::getHandlerId, query.getHandlerId())
                .ge(query.getOccurDateFrom() != null, CaseMain::getOccurDate, query.getOccurDateFrom())
                .le(query.getOccurDateTo() != null, CaseMain::getOccurDate, query.getOccurDateTo())
                .eq(query.getArchiveStatus() != null, CaseMain::getArchiveStatus, query.getArchiveStatus());

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(CaseMain::getCaseNo, kw)
                    .or().like(CaseMain::getCaseName, kw)
                    .or().like(CaseMain::getEmployeeName, kw));
        }
        if (Boolean.TRUE.equals(query.getOverdue())) {
            wrapper.inSql(CaseMain::getId,
                    "SELECT DISTINCT case_id FROM deadline_reminder "
                            + "WHERE status = 'OVERDUE' AND start_time IS NOT NULL");
        }
        if (Boolean.TRUE.equals(query.getDueSoon())) {
            // 临近超期：最近未完成期限落在今天 ~ 3 天内，且该案件没有任何超期节点
            wrapper.inSql(CaseMain::getId,
                    "SELECT DISTINCT case_id FROM deadline_reminder WHERE status <> 'DONE' "
                            + "AND start_time IS NOT NULL "
                            + "AND deadline_date >= CURDATE() "
                            + "AND deadline_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)");
            wrapper.notInSql(CaseMain::getId,
                    "SELECT DISTINCT case_id FROM deadline_reminder "
                            + "WHERE status = 'OVERDUE' AND start_time IS NOT NULL");
        }
    }

    /**
     * 数据权限：ALL 全量；DEPT 本部门及子部门；SELF 仅本人作为成员参与的案件。
     * SELF 使用参数占位符而非字符串拼接，避免 SQL 注入。
     */
    void applyDataScope(LambdaQueryWrapper<CaseMain> wrapper) {
        LoginUser current = UserContext.get();
        // 纯审计员只审计日志，不参与案件业务（数据库设计文档 7.4）
        if (isPureAuditor(current)) {
            wrapper.eq(CaseMain::getId, -1L);
            return;
        }
        if (current.isAllScope()) {
            return;
        }
        if ("DEPT".equals(current.getDataScope())) {
            List<Long> deptIds = deptService.selfAndDescendantIds(current.getDeptId());
            if (deptIds.isEmpty()) {
                wrapper.eq(CaseMain::getId, -1L);
                return;
            }
            List<String> ids = deptIds.stream().map(String::valueOf).collect(Collectors.toList());
            wrapper.in(CaseMain::getDepartmentId, ids);
            return;
        }
        wrapper.apply("id IN (SELECT case_id FROM case_member WHERE user_id = {0})", current.getUserId());
    }

    /** 数据范围三元组：供需要下推到 SQL 的统计复用，口径与 {@link #applyDataScope} 保持一致 */
    public static class DataScope {
        public boolean allScope;
        public String userId;
        public List<String> deptIds;
    }

    public DataScope dataScope() {
        LoginUser current = UserContext.get();
        boolean pureAuditor = isPureAuditor(current);
        DataScope scope = new DataScope();
        scope.allScope = !pureAuditor && current.isAllScope();
        // 纯审计员不参与业务统计：用不存在的用户命中，结果恒为空
        scope.userId = pureAuditor ? "__none__" : current.getUserId();
        if (!scope.allScope && "DEPT".equals(current.getDataScope())) {
            scope.deptIds = deptService.selfAndDescendantIds(current.getDeptId()).stream()
                    .map(String::valueOf).collect(Collectors.toList());
        }
        return scope;
    }

    private List<CaseVO> convertToVOs(List<CaseMain> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> userNames = loadUserNames(records);
        Map<Long, DeadlineReminder> overdueMap = loadOverdue(records);
        Map<Long, DeadlineReminder> nearestMap = loadNearestDeadline(records);
        Map<Long, DeadlineReminder> currentRows = loadCurrentNodeRows(records);
        Map<String, Map<String, WfNodeDef>> nodeCache = new HashMap<>();

        LocalDate today = LocalDate.now();
        List<CaseVO> result = new ArrayList<>(records.size());
        for (CaseMain entity : records) {
            CaseVO vo = new CaseVO();
            copyBase(entity, vo, userNames);
            DeadlineReminder overdue = overdueMap.get(entity.getId());
            if (overdue != null) {
                vo.setOverdue(true);
                vo.setOverdueDays(ChronoUnit.DAYS.between(overdue.getDeadlineDate(), today));
            } else if (entity.getArchiveStatus() == null || entity.getArchiveStatus() == 0) {
                // 已结案/归档的案件不再提示临近超期
                DeadlineReminder nearest = nearestMap.get(entity.getId());
                if (nearest != null && nearest.getDeadlineDate() != null) {
                    long days = ChronoUnit.DAYS.between(today, nearest.getDeadlineDate());
                    // 已过期的由 overdue 分支负责，这里只覆盖「今天到期 ~ 3 天内到期」
                    if (days >= 0 && days <= DUE_SOON_DAYS) {
                        vo.setDueSoon(true);
                        vo.setDaysToDeadline(days);
                        vo.setNearestDeadline(nearest.getDeadlineDate());
                    }
                }
            }
            // 当前节点截止日期：发生日期 + 节点时限（工作日），人工调整值优先
            WfNodeDef currentNode = nodeCache
                    .computeIfAbsent(entity.getCaseType(), type -> flowService.enabledNodes(type).stream()
                            .collect(Collectors.toMap(WfNodeDef::getStatusCode, n -> n, (a, b) -> a)))
                    .get(entity.getStatus());
            DeadlineReminder currentRow = currentRows.get(entity.getId());
            Integer days = currentNode == null ? null : WorkDayUtils.parseDays(currentNode.getTimeLimit());
            LocalDate autoDate = days == null ? null
                    : WorkDayUtils.plusWorkingDays(currentRow != null && currentRow.getHandleDate() != null
                            ? currentRow.getHandleDate() : DeadlineService.baseDate(entity), days);
            LocalDate manualDate = currentRow == null ? null : currentRow.getManualDate();
            vo.setCurrentDeadline(manualDate != null ? manualDate : autoDate);
            vo.setDeadlineStarted(currentRow != null && currentRow.getStartTime() != null);

            result.add(vo);
        }
        return result;
    }

    /** 当前节点对应的期限记录（node_code 等于案件当前状态），用于列表展示截止日期与开启状态 */
    private Map<Long, DeadlineReminder> loadCurrentNodeRows(List<CaseMain> records) {
        List<Long> caseIds = records.stream().map(CaseMain::getId).collect(Collectors.toList());
        if (caseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .in(DeadlineReminder::getCaseId, caseIds)
                .orderByDesc(DeadlineReminder::getId));
        Map<Long, String> statusMap = new HashMap<>();
        for (CaseMain record : records) {
            statusMap.put(record.getId(), record.getStatus());
        }
        Map<Long, DeadlineReminder> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            // 倒序取到的最新一条，同节点多次进入时以最新为准
            if (item.getNodeCode().equals(statusMap.get(item.getCaseId()))) {
                map.putIfAbsent(item.getCaseId(), item);
            }
        }
        return map;
    }

    /** 临近超期的判定阈值（天）：距截止日不足 7 个自然日 */
    private static final long DUE_SOON_DAYS = 7;

    /**
     * 批量取每个案件最近的一条未完成期限。
     * 与 loadOverdue 分开查询：一条案件可能既有超期节点又有临近节点，
     * 混在一起按截止日排序会取错，超期状态的展示必须以 OVERDUE 记录为准。
     */
    private Map<Long, DeadlineReminder> loadNearestDeadline(List<CaseMain> records) {
        List<Long> caseIds = records.stream().map(CaseMain::getId).collect(Collectors.toList());
        if (caseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .in(DeadlineReminder::getCaseId, caseIds)
                .ne(DeadlineReminder::getStatus, "DONE")
                .isNotNull(DeadlineReminder::getStartTime)
                .orderByAsc(DeadlineReminder::getDeadlineDate));
        Map<Long, String> statusMap = new HashMap<>();
        for (CaseMain record : records) {
            statusMap.put(record.getId(), record.getStatus());
        }
        Map<Long, DeadlineReminder> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            // 同上：只保留当前节点的期限
            if (!item.getNodeCode().equals(statusMap.get(item.getCaseId()))) {
                continue;
            }
            map.putIfAbsent(item.getCaseId(), item);
        }
        return map;
    }

    private Map<String, String> loadUserNames(List<CaseMain> records) {
        Set<String> ids = new HashSet<>();
        for (CaseMain c : records) {
            if (c.getHandlerId() != null) {
                ids.add(c.getHandlerId());
            }
            if (c.getOwnerId() != null) {
                ids.add(c.getOwnerId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserId, ids));
        Map<String, String> map = new HashMap<>();
        for (SysUser user : users) {
            map.put(user.getUserId(), user.getUserName());
        }
        return map;
    }

    /** 用户名称兜底为原始账号，避免界面出现空白 */
    private String namesOrId(Map<String, String> names, String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return names.getOrDefault(userId, userId);
    }

    private Map<Long, DeadlineReminder> loadOverdue(List<CaseMain> records) {
        List<Long> caseIds = records.stream().map(CaseMain::getId).collect(Collectors.toList());
        Map<Long, String> currentStatus = new HashMap<>();
        for (CaseMain record : records) {
            currentStatus.put(record.getId(), record.getStatus());
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .in(DeadlineReminder::getCaseId, caseIds)
                .eq(DeadlineReminder::getStatus, "OVERDUE")
                .isNotNull(DeadlineReminder::getStartTime)
                .orderByAsc(DeadlineReminder::getDeadlineDate));
        Map<Long, DeadlineReminder> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            // 只看案件当前所在节点：已离开节点的期限不影响列表状态，与详情页口径一致
            if (!item.getNodeCode().equals(currentStatus.get(item.getCaseId()))) {
                continue;
            }
            // 同一案件多个超期节点时取最早的一个计算超期天数
            if (!map.containsKey(item.getCaseId())) {
                map.put(item.getCaseId(), item);
            }
        }
        return map;
    }

    private void copyBase(CaseMain entity, CaseVO vo, Map<String, String> userNames) {
        vo.setId(entity.getId());
        vo.setCaseNo(entity.getCaseNo());
        vo.setCaseType(entity.getCaseType());
        vo.setCaseTypeName(CaseDict.typeName(entity.getCaseType()));
        vo.setCaseName(entity.getCaseName());
        vo.setEmployeeId(entity.getEmployeeId());
        vo.setEmployeeName(maskedName(entity));
        vo.setDepartmentId(entity.getDepartmentId());
        vo.setDepartmentName(entity.getDepartmentName());
        vo.setPlantArea(entity.getPlantArea());
        vo.setWorkshopTeam(entity.getWorkshopTeam());
        vo.setLeaderL1(entity.getLeaderL1());
        vo.setLeaderL2(entity.getLeaderL2());
        vo.setOccurDate(entity.getOccurDate());
        vo.setStatus(entity.getStatus());
        vo.setStatusName(CaseDict.statusName(entity.getStatus()));
        vo.setOwnerId(entity.getOwnerId());
        vo.setOwnerName(userNames.get(entity.getOwnerId()));
        vo.setHandlerId(entity.getHandlerId());
        vo.setHandlerName(userNames.get(entity.getHandlerId()));
        vo.setArchiveStatus(entity.getArchiveStatus());
        vo.setCloseDate(entity.getCloseDate());
        vo.setCloseType(entity.getCloseType());
        vo.setCloseTypeName(CaseDict.closeTypeName(entity.getCloseType()));
        vo.setArchivePages(entity.getArchivePages());
        vo.setArchiveYears(entity.getArchiveYears());
        vo.setProgress(flowService.progress(entity.getCaseType(), entity.getStatus()));
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
    }

    public CaseDetailVO detail(Long id) {
        CaseMain entity = loadCase(id);
        assertCanView(entity);
        LoginUser current = UserContext.get();

        CaseDetailVO vo = new CaseDetailVO();
        Map<String, String> userNames = loadUserNames(Collections.singletonList(entity));
        copyBase(entity, vo, userNames);
        vo.setDescription(entity.getDescription());

        boolean sensitive = canViewSensitive(entity);
        vo.setSensitiveMasked(!sensitive);
        if (sensitive) {
            vo.setIdCardNo(cryptoUtil.decrypt(entity.getIdCardNo()));
        } else {
            String plain = cryptoUtil.decrypt(entity.getIdCardNo());
            vo.setIdCardNo(plain == null ? null : CryptoUtil.maskIdCard(plain));
        }

        vo.setMembers(loadMembers(entity.getId()));
        vo.setStatusLogs(loadStatusLogs(entity.getId()));
        if (CaseStateMachine.TYPE_INJURY.equals(entity.getCaseType())) {
            CaseInjuryExt ext = injuryExtMapper.selectById(entity.getId());
            if (ext != null) {
                InjuryExtDTO dto = new InjuryExtDTO();
                dto.setInjuryDate(ext.getInjuryDate());
                dto.setInjuryPlace(ext.getInjuryPlace());
                dto.setInjuryReason(ext.getInjuryReason());
                dto.setApplyDate(ext.getApplyDate());
                dto.setRecognizeDate(ext.getRecognizeDate());
                dto.setRecognizeResult(ext.getRecognizeResult());
                dto.setAppraisalDate(ext.getAppraisalDate());
                dto.setAppraisalLevel(ext.getAppraisalLevel());
                dto.setRestDays(ext.getRestDays());
                // 无权限时金额不下发明文
                dto.setTreatmentAmount(sensitive ? ext.getTreatmentAmount() : null);
                dto.setSubsidyDisabilityStatus(ext.getSubsidyDisabilityStatus());
                dto.setSubsidyMedicalStatus(ext.getSubsidyMedicalStatus());
                dto.setSubsidyEmploymentStatus(ext.getSubsidyEmploymentStatus());
                dto.setSubsidyDisabilityAmount(sensitive ? ext.getSubsidyDisabilityAmount() : null);
                dto.setSubsidyMedicalAmount(sensitive ? ext.getSubsidyMedicalAmount() : null);
                dto.setSubsidyEmploymentAmount(sensitive ? ext.getSubsidyEmploymentAmount() : null);
                dto.setDisputeFlag(ext.getDisputeFlag());
                vo.setInjury(dto);
            }
        } else if (CaseStateMachine.TYPE_ARBITRATION.equals(entity.getCaseType())) {
            CaseArbitrationExt ext = arbitrationExtMapper.selectById(entity.getId());
            if (ext != null) {
                ArbitrationExtDTO dto = new ArbitrationExtDTO();
                dto.setArbitrationNo(ext.getArbitrationNo());
                dto.setApplyDate(ext.getApplyDate());
                dto.setAcceptDate(ext.getAcceptDate());
                dto.setHearingDate(ext.getHearingDate());
                dto.setResultType(ext.getResultType());
                dto.setAwardAmount(sensitive ? ext.getAwardAmount() : null);
                dto.setPerformAmount(sensitive ? ext.getPerformAmount() : null);
                dto.setPerformStatus(ext.getPerformStatus());
                dto.setPerformDate(ext.getPerformDate());
                vo.setArbitration(dto);
            }
        }
        vo.setRelations(loadRelations(entity));
        vo.setNodeDeadlines(buildNodeDeadlines(entity, vo.getStatusLogs()));
        vo.setActions(buildActions(entity, current));
        fillTransitionTargets(vo, entity, current);
        return vo;
    }

    /**
     * 各流程节点的截止日期。
     *
     * 口径：以案件「发生日期」（立案时登记）为起算日 + 该节点时限天数（工作日，跳过周六周日）；
     * 未到达的节点不返回；已办结节点标记 DONE，当前节点按剩余天数给出
     * OVERDUE / DUE_SOON（不足 7 天）/ NORMAL。
     * 人工调整过截止日期的节点，以 deadline_reminder.manual_date 为准。
     */
    private List<CaseDetailVO.NodeDeadlineVO> buildNodeDeadlines(CaseMain entity, List<CaseDetailVO.StatusLogVO> logs) {
        List<WfNodeDef> nodes = flowService.enabledNodes(entity.getCaseType());
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, LocalDate> manualDates = deadlineService.manualDates(entity.getId());
        Map<String, LocalDate> handleDates = deadlineService.handleDates(entity.getId());
        Map<String, LocalDateTime> startedAt = deadlineService.startedAt(entity.getId());
        // 起算日：发生日期，缺省退回建档时间
        LocalDate base = DeadlineService.baseDate(entity);

        int currentIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getStatusCode().equals(entity.getStatus())) {
                currentIndex = i;
                break;
            }
        }

        LocalDate today = LocalDate.now();
        List<CaseDetailVO.NodeDeadlineVO> result = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            WfNodeDef node = nodes.get(i);
            if (currentIndex >= 0 && i > currentIndex) {
                break;
            }
            Integer days = WorkDayUtils.parseDays(node.getTimeLimit());
            LocalDate manual = manualDates.get(node.getStatusCode());
            if (days == null && manual == null) {
                continue;
            }
            LocalDate handleDate = handleDates.get(node.getStatusCode());
            LocalDate nodeBase = handleDate != null ? handleDate : base;
            LocalDate autoDate = days == null ? null : WorkDayUtils.plusWorkingDays(nodeBase, days);
            LocalDate deadline = manual != null ? manual : autoDate;
            if (deadline == null) {
                continue;
            }

            CaseDetailVO.NodeDeadlineVO vo = new CaseDetailVO.NodeDeadlineVO();
            vo.setStatusCode(node.getStatusCode());
            vo.setNodeName(node.getNodeName());
            vo.setEnterDate(nodeBase);
            vo.setHandleDate(handleDate);
            vo.setAutoDate(autoDate);
            vo.setDeadlineDate(deadline);
            vo.setManual(manual != null);
            LocalDateTime startTime = startedAt.get(node.getStatusCode());
            vo.setStarted(startTime != null);
            vo.setStartTime(startTime);
            if (i < currentIndex || stateMachine.isArchived(entity.getArchiveStatus())) {
                vo.setState("DONE");
            } else if (startTime == null) {
                // 未提交开启当前节点：只展示截止日期，不计剩余天数与超期
                vo.setState("NOT_STARTED");
            } else {
                long remain = ChronoUnit.DAYS.between(today, deadline);
                vo.setRemainDays(remain);
                if (remain < 0) {
                    vo.setState("OVERDUE");
                } else if (remain < DeadlineService.DUE_SOON_DAYS) {
                    vo.setState("DUE_SOON");
                } else {
                    vo.setState("NORMAL");
                }
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 可选流转目标：推进支持跳过中间节点（如立案直接到认定），退回支持回到更早节点。
     * 与 actions 分开返回，前端据此渲染下拉，不必按直接后继写死按钮。
     */
    private void fillTransitionTargets(CaseDetailVO vo, CaseMain entity, LoginUser current) {
        if (stateMachine.isArchived(entity.getArchiveStatus())) {
            return;
        }
        if (CaseStateMachine.DRAFT.equals(entity.getStatus())) {
            vo.getForwardTargets().add(new CaseDetailVO.ActionVO(
                    CaseStateMachine.FILED, flowService.statusLabel(entity.getCaseType(), CaseStateMachine.FILED)));
            return;
        }
        if (!canOperate(entity, current) || CaseStateMachine.CLOSED.equals(entity.getStatus())) {
            return;
        }
        for (String code : flowService.forwardTargets(entity.getCaseType(), entity.getStatus())) {
            vo.getForwardTargets().add(new CaseDetailVO.ActionVO(
                    code, flowService.statusLabel(entity.getCaseType(), code)));
        }
        List<String> rollbackCodes = new ArrayList<>(
                flowService.rollbackTargets(entity.getCaseType(), entity.getStatus()));
        // 分支流程按实际来源排序：裁定→履行后，退回默认回到裁定而不是主链上的裁决
        CaseStatusLog lastIn = statusLogMapper.selectOne(new LambdaQueryWrapper<CaseStatusLog>()
                .eq(CaseStatusLog::getCaseId, entity.getId())
                .eq(CaseStatusLog::getToStatus, entity.getStatus())
                .orderByDesc(CaseStatusLog::getId)
                .last("LIMIT 1"));
        if (lastIn != null && StringUtils.hasText(lastIn.getFromStatus())
                && rollbackCodes.remove(lastIn.getFromStatus())) {
            rollbackCodes.add(0, lastIn.getFromStatus());
        }
        for (String code : rollbackCodes) {
            vo.getRollbackTargets().add(new CaseDetailVO.ActionVO(
                    code, flowService.statusLabel(entity.getCaseType(), code)));
        }
    }

    /** 会签人列表：会签发起只需成员 ID，不必加载整份案件详情 */
    public List<String> countersignerIds(Long caseId) {
        return caseMemberMapper.selectList(new LambdaQueryWrapper<CaseMember>()
                        .eq(CaseMember::getCaseId, caseId)
                        .eq(CaseMember::getMemberRole, "COUNTERSIGN"))
                .stream().map(CaseMember::getUserId).collect(Collectors.toList());
    }

    private List<CaseDetailVO.MemberVO> loadMembers(Long caseId) {
        List<CaseMember> members = caseMemberMapper.selectList(new LambdaQueryWrapper<CaseMember>()
                .eq(CaseMember::getCaseId, caseId)
                .orderByAsc(CaseMember::getId));
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> userIds = members.stream().map(CaseMember::getUserId).collect(Collectors.toSet());
        Map<String, String> names = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUserId, userIds)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        List<CaseDetailVO.MemberVO> result = new ArrayList<>();
        for (CaseMember member : members) {
            CaseDetailVO.MemberVO vo = new CaseDetailVO.MemberVO();
            vo.setUserId(member.getUserId());
            vo.setUserName(names.getOrDefault(member.getUserId(), member.getUserId()));
            vo.setMemberRole(member.getMemberRole());
            vo.setMemberRoleName(CaseDict.memberRoleName(member.getMemberRole()));
            vo.setJoinTime(member.getJoinTime());
            result.add(vo);
        }
        return result;
    }

    private List<CaseDetailVO.StatusLogVO> loadStatusLogs(Long caseId) {
        List<CaseStatusLog> logs = statusLogMapper.selectList(new LambdaQueryWrapper<CaseStatusLog>()
                .eq(CaseStatusLog::getCaseId, caseId)
                .orderByDesc(CaseStatusLog::getOperateTime)
                .orderByDesc(CaseStatusLog::getId));
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> operators = logs.stream().map(CaseStatusLog::getOperatorId).collect(Collectors.toSet());
        Map<String, String> names = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUserId, operators)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        List<CaseDetailVO.StatusLogVO> result = new ArrayList<>();
        for (CaseStatusLog item : logs) {
            CaseDetailVO.StatusLogVO vo = new CaseDetailVO.StatusLogVO();
            vo.setFromStatus(item.getFromStatus());
            vo.setFromStatusName(item.getFromStatus() == null ? null : CaseDict.statusName(item.getFromStatus()));
            vo.setToStatus(item.getToStatus());
            vo.setToStatusName(CaseDict.statusName(item.getToStatus()));
            vo.setAction(item.getAction());
            vo.setOperatorId(item.getOperatorId());
            vo.setOperatorName(names.getOrDefault(item.getOperatorId(), item.getOperatorId()));
            vo.setOpinion(item.getOpinion());
            vo.setOperateTime(item.getOperateTime());
            result.add(vo);
        }
        return result;
    }

    private List<CaseDetailVO.RelationVO> loadRelations(CaseMain entity) {
        // 关联只存一条（主案 ID 较小者），两侧案件详情都要能看到对方
        List<CaseRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<CaseRelation>()
                .and(w -> w.eq(CaseRelation::getCaseId, entity.getId())
                        .or().eq(CaseRelation::getRelatedCaseId, entity.getId())));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Long> otherIdOf = new HashMap<>();
        for (CaseRelation relation : relations) {
            boolean selfIsMain = entity.getId().equals(relation.getCaseId());
            otherIdOf.put(relation.getId(), selfIsMain ? relation.getRelatedCaseId() : relation.getCaseId());
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(otherIdOf.values()));
        Map<Long, CaseMain> relatedMap = caseMainMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CaseMain::getId, Function.identity(), (a, b) -> a));

        // 关联案件承办人 + 关联建立人：一次查库，避免逐条查询
        Set<String> userIds = new HashSet<>();
        for (CaseMain related : relatedMap.values()) {
            if (related.getHandlerId() != null) {
                userIds.add(related.getHandlerId());
            }
        }
        for (CaseRelation relation : relations) {
            if (relation.getCreateBy() != null) {
                userIds.add(relation.getCreateBy());
            }
        }
        Map<String, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getUserId, userIds))) {
                userNames.put(user.getUserId(), user.getUserName());
            }
        }

        List<CaseDetailVO.RelationVO> result = new ArrayList<>();
        for (CaseRelation relation : relations) {
            Long relatedId = otherIdOf.get(relation.getId());
            CaseMain related = relatedMap.get(relatedId);
            CaseDetailVO.RelationVO vo = new CaseDetailVO.RelationVO();
            vo.setId(relation.getId());
            vo.setRelatedCaseId(relatedId);
            vo.setRelationType(relation.getRelationType());
            vo.setRelationTypeName(CaseDict.RELATION_TYPE_NAME.getOrDefault(
                    relation.getRelationType(), relation.getRelationType()));
            vo.setRelationDesc(relation.getRelationDesc());
            vo.setCreateTime(relation.getCreateTime());
            vo.setCreateByName(namesOrId(userNames, relation.getCreateBy()));
            if (related != null) {
                vo.setRelatedCaseNo(related.getCaseNo());
                vo.setRelatedCaseName(related.getCaseName());
                vo.setRelatedCaseType(related.getCaseType());
                vo.setRelatedCaseTypeName(CaseDict.TYPE_NAME.getOrDefault(
                        related.getCaseType(), related.getCaseType()));
                vo.setRelatedEmployeeName(related.getEmployeeName());
                vo.setRelatedPlantArea(related.getPlantArea());
                vo.setRelatedWorkshopTeam(related.getWorkshopTeam());
                vo.setRelatedDepartmentName(related.getDepartmentName());
                vo.setRelatedHandlerName(namesOrId(userNames, related.getHandlerId()));
                vo.setRelatedOccurDate(related.getOccurDate());
                vo.setRelatedStatusCode(related.getStatus());
                vo.setRelatedStatusName(CaseDict.statusName(related.getStatus()));
                vo.setRelatedStatus(CaseDict.statusName(related.getStatus()));
                vo.setRelatedArchiveStatus(related.getArchiveStatus());
            }
            result.add(vo);
        }
        return result;
    }

    // ==================== 状态流转 ====================

    /**
     * 人工调整当前节点截止日期。
     *
     * 默认按「发生日期 + 节点时限（工作日）」自动推算，这里允许承办人按实际情况改写，
     * 调整后详情页、期限中心与提醒扫描都以人工值为准。
     */
    public CaseDetailVO.NodeDeadlineVO adjustNodeDeadline(Long id, String nodeCode, String deadlineDate, String reason) {
        return adjustNodeDeadline(id, nodeCode, deadlineDate, reason, null);
    }

    /** 调整当前节点办理日期或截止日期；办理日期变更时按规则重算截止日期 */
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailVO.NodeDeadlineVO adjustNodeDeadline(Long id, String nodeCode, String deadlineDate,
                                                           String reason, String handleDate) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        LocalDate handle = null;
        if (StringUtils.hasText(handleDate)) {
            try {
                handle = LocalDate.parse(handleDate);
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "办理日期格式应为 yyyy-MM-dd");
            }
        }
        LocalDate date = null;
        if (StringUtils.hasText(deadlineDate)) {
            try {
                date = LocalDate.parse(deadlineDate);
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "截止日期格式应为 yyyy-MM-dd");
            }
        }
        if (date == null && handle == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择办理日期或截止日期");
        }
        return deadlineService.adjustDeadline(entity.getId(), nodeCode, date, reason, handle);
    }

    /** 开启当前节点计时：提交后该节点才按截止日期判断超期 */
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailVO.NodeDeadlineVO openNode(Long id, String nodeCode) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        return deadlineService.openNode(entity.getId(), nodeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, String opinion) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        if (!CaseStateMachine.DRAFT.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "仅草稿状态可提交立案");
        }
        doTransition(entity, CaseStateMachine.FILED, "SUBMIT", opinion);
    }

    @Transactional(rollbackFor = Exception.class)
    public void transition(Long id, TransitionRequest req) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        assertOperatePermission(entity);

        String target = req.getToStatus();
        if (!StringUtils.hasText(target)) {
            List<String> next = flowService.nextStatuses(entity.getCaseType(), entity.getStatus());
            if (next.size() != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在多个后继状态，请明确指定目标状态");
            }
            target = next.get(0);
        }
        if (CaseStateMachine.CLOSED.equals(target)) {
            throw new BizException(ErrorCode.FORBIDDEN, "结案需走审批，请使用结案操作");
        }
        if (!flowService.canTransit(entity.getCaseType(), entity.getStatus(), target)
                && !flowService.forwardTargets(entity.getCaseType(), entity.getStatus()).contains(target)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    String.format("不允许从「%s」流转到「%s」",
                            CaseDict.statusName(entity.getStatus()), CaseDict.statusName(target)));
        }
        String action = StringUtils.hasText(req.getAction()) ? req.getAction() : "TRANSIT";
        // 离开的节点即本次办结的节点，先取原状态，流转后据此回填档案结论
        String from = entity.getStatus();
        doTransition(entity, target, action, req.getOpinion());
        // 跳级推进时，被跳过的中间节点一并视为办结，否则其期限会一直挂着提醒
        for (String skipped : flowService.predecessors(entity.getCaseType(), target)) {
            deadlineService.completeByStatus(entity.getId(), skipped);
        }
        backfillFromNode(entity, from, req);
    }

    /**
     * 节点办结回填：办理「认定」「鉴定」「立案」「裁决」节点后，把结论同步进案件档案。
     *
     * 取值优先级：前端显式传入 > 档案已有值 > 按节点推断的默认值。
     * 默认值只在档案为空时兜底，不覆盖人工已填写的结论（例如「不予认定」不会被改成「已认定」）。
     */
    private void backfillFromNode(CaseMain entity, String fromStatus, TransitionRequest req) {
        if (entity == null || !StringUtils.hasText(fromStatus) || req == null) {
            return;
        }
        LocalDate today = LocalDate.now();

        if (CaseStateMachine.TYPE_INJURY.equals(entity.getCaseType())) {
            CaseInjuryExt ext = injuryExtMapper.selectById(entity.getId());
            if (ext == null) {
                return;
            }
            if ("RECOGNITION".equals(fromStatus)) {
                if (StringUtils.hasText(req.getRecognizeResult())) {
                    ext.setRecognizeResult(req.getRecognizeResult());
                } else if (!StringUtils.hasText(ext.getRecognizeResult())
                        || "DECLARING".equals(ext.getRecognizeResult())
                        || "NOT_DECLARED".equals(ext.getRecognizeResult())) {
                    // 认定节点办结默认视为已认定，人工可另选「不予认定」
                    ext.setRecognizeResult("RECOGNIZED");
                }
                if (ext.getRecognizeDate() == null) {
                    ext.setRecognizeDate(today);
                }
                injuryExtMapper.updateById(ext);
            } else if ("APPRAISAL".equals(fromStatus)) {
                if (StringUtils.hasText(req.getAppraisalLevel())) {
                    ext.setAppraisalLevel(req.getAppraisalLevel());
                }
                // 休假天数只在本次填写了才覆盖，未填不猜测
                if (req.getRestDays() != null) {
                    ext.setRestDays(req.getRestDays());
                }
                if (ext.getAppraisalDate() == null) {
                    ext.setAppraisalDate(today);
                }
                injuryExtMapper.updateById(ext);
            }
            return;
        }

        if (CaseStateMachine.TYPE_ARBITRATION.equals(entity.getCaseType())) {
            CaseArbitrationExt ext = arbitrationExtMapper.selectById(entity.getId());
            if (ext == null) {
                return;
            }
            if (CaseStateMachine.FILED.equals(fromStatus)) {
                if (StringUtils.hasText(req.getAcceptDate())) {
                    ext.setAcceptDate(LocalDate.parse(req.getAcceptDate()));
                } else if (ext.getAcceptDate() == null) {
                    // 立案节点办结即代表已受理
                    ext.setAcceptDate(today);
                }
                arbitrationExtMapper.updateById(ext);
            } else if ("PERFORM".equals(fromStatus)) {
                if (req.getPerformAmount() != null) {
                    ext.setPerformAmount(req.getPerformAmount());
                }
                if (ext.getPerformDate() == null) {
                    ext.setPerformDate(today);
                }
                arbitrationExtMapper.updateById(ext);
            } else if ("AWARD".equals(fromStatus)) {
                if (StringUtils.hasText(req.getResultType())) {
                    ext.setResultType(req.getResultType());
                } else if (!StringUtils.hasText(ext.getResultType())) {
                    ext.setResultType("AWARD");
                }
                arbitrationExtMapper.updateById(ext);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String target, String opinion) {
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        assertOperatePermission(entity);
        if (!flowService.canRollback(entity.getCaseType(), entity.getStatus())) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "当前状态不支持退回");
        }
        String previous = flowService.previousStatus(entity.getCaseType(), entity.getStatus());
        String from = entity.getStatus();
        String to = previous;
        // 允许退回到更早的节点（如争议处理直接退回立案），目标必须在该节点的上游集合内
        if (StringUtils.hasText(target)) {
            List<String> allowed = flowService.rollbackTargets(entity.getCaseType(), from);
            if (!allowed.contains(target)) {
                throw new BizException(ErrorCode.STATUS_CONFLICT,
                        String.format("不允许从「%s」退回「%s」",
                                CaseDict.statusName(from), CaseDict.statusName(target)));
            }
            to = target;
        }
        doTransition(entity, to, "ROLLBACK", opinion);
        // 退回后该节点需要重新办理，期限重新打开（按截止日回落为待办或超期）
        deadlineService.reopenByStatus(entity.getId(), to);
        // 跨节点退回时，被越过（原已办结、退回后需重办）的节点期限同样重开，含退回前的当前节点
        List<String> downstream = flowService.forwardTargets(entity.getCaseType(), to);
        List<String> toReopen = new ArrayList<>(flowService.predecessors(entity.getCaseType(), from));
        toReopen.add(from);
        for (String node : toReopen) {
            if (downstream.contains(node)) {
                deadlineService.reopenByStatus(entity.getId(), node);
            }
        }
    }

    /** 结案：需审批权限（案件管理员/部门负责人/系统管理员），承办人不可直接结案 */
    /**
     * 删除案件：软删除（deleted=1），进入回收站，可在回收站恢复。
     * 权限：承办人 / 负责人 / 案件管理员 / 系统管理员；已归档案件仅系统管理员可删。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CaseMain entity = loadCase(id);
        LoginUser current = UserContext.get();
        boolean admin = current.hasRole(ROLE_SYS_ADMIN) || current.hasRole(ROLE_CASE_ADMIN);
        if (!admin
                && !current.getUserId().equals(entity.getHandlerId())
                && !current.getUserId().equals(entity.getOwnerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅承办人、负责人或案件管理员可删除案件");
        }
        if (entity.getArchiveStatus() != null && entity.getArchiveStatus() == 1
                && !current.hasRole(ROLE_SYS_ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN, "已归档案件仅系统管理员可删除");
        }
        caseMainMapper.update(null, new LambdaUpdateWrapper<CaseMain>()
                .eq(CaseMain::getId, id)
                .set(CaseMain::getDeleted, 1)
                .set(CaseMain::getUpdateTime, LocalDateTime.now()));
        auditService.success("案件中心", "删除案件", String.valueOf(id), "案号=" + entity.getCaseNo() + "，进入回收站");
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, TransitionRequest.CloseRequest req) {
        String opinion = req == null ? null : req.getOpinion();
        CaseMain entity = loadCase(id);
        assertEditable(entity);
        if (!hasApprovePermission(entity)) {
            throw new BizException(ErrorCode.FORBIDDEN, "结案需审批权限，请联系案件管理员或部门负责人");
        }
        List<String> next = flowService.nextStatuses(entity.getCaseType(), entity.getStatus());
        if (!next.contains(CaseStateMachine.CLOSED)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    String.format("当前状态「%s」不允许结案", CaseDict.statusName(entity.getStatus())));
        }
        // 结案意味着案件全部办结：当前节点（如「履行」）自身超期，以及此前流转没关闭的历史节点，
        // 都不应再阻断结案，否则案件会永久卡在最后一步。
        // 先记录超期数量用于留痕，再把该案件所有期限节点统一关闭。
        int overdue = deadlineService.countOverdue(entity.getId());
        deadlineService.completeAll(entity.getId());

        entity.setCloseDate(LocalDate.now());
        if (req != null) {
            if (StringUtils.hasText(req.getCloseType())) {
                entity.setCloseType(req.getCloseType());
            }
            if (req.getArchivePages() != null) {
                entity.setArchivePages(req.getArchivePages());
            }
            if (req.getArchiveYears() != null) {
                entity.setArchiveYears(req.getArchiveYears());
            }
        }
        String from = entity.getStatus();
        doTransition(entity, CaseStateMachine.CLOSED, "CLOSE", opinion);
        // 履行节点直接结案时，办理区填写的履行款随结案一并回填档案
        if (req != null && req.getPerformAmount() != null) {
            TransitionRequest result = new TransitionRequest();
            result.setPerformAmount(req.getPerformAmount());
            backfillFromNode(entity, from, result);
        }
        backfillOnClose(entity, req);
        finishWorkflow(entity.getId());
        if (overdue > 0) {
            auditService.success("案件中心", "结案", String.valueOf(id),
                    String.format("案号=%s，结案时存在 %d 个超期节点，已随结案一并关闭",
                            entity.getCaseNo(), overdue));
        }
    }

    /** 结案回填：结案方式即裁决/调解结果，写入档案供台账与详情展示 */
    private void backfillOnClose(CaseMain entity, TransitionRequest.CloseRequest req) {
        if (entity == null || !CaseStateMachine.TYPE_ARBITRATION.equals(entity.getCaseType())) {
            return;
        }
        String closeType = req == null ? null : req.getCloseType();
        String result;
        if ("MEDIATION".equals(closeType)) {
            result = "MEDIATION";
        } else if ("AWARD".equals(closeType) || "TREATMENT_SETTLED".equals(closeType)) {
            result = "AWARD";
        } else if ("WITHDRAW".equals(closeType)) {
            result = "WITHDRAW";
        } else {
            return;
        }
        CaseArbitrationExt ext = arbitrationExtMapper.selectById(entity.getId());
        if (ext == null) {
            return;
        }
        ext.setResultType(result);
        arbitrationExtMapper.updateById(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        CaseMain entity = loadCase(id);
        if (stateMachine.isArchived(entity.getArchiveStatus())) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "案件已归档");
        }
        if (!CaseStateMachine.CLOSED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "仅已结案案件可归档");
        }
        LoginUser current = UserContext.get();
        if (!current.hasRole(ROLE_SYS_ADMIN) && !current.hasRole(ROLE_CASE_ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN, "归档需案件管理员或系统管理员权限");
        }
        entity.setArchiveStatus(1);
        entity.setStatus(CaseStateMachine.ARCHIVED);
        entity.setUpdateTime(LocalDateTime.now());
        caseMainMapper.updateById(entity);
        logStatus(entity.getId(), CaseStateMachine.CLOSED, CaseStateMachine.ARCHIVED, "ARCHIVE", "结案归档");
        deadlineService.completeAll(entity.getId());
    }

    private void doTransition(CaseMain entity, String target, String action, String opinion) {
        String from = entity.getStatus();
        entity.setStatus(target);
        entity.setUpdateTime(LocalDateTime.now());
        caseMainMapper.updateById(entity);
        logStatus(entity.getId(), from, target, action, opinion);
        // 离开的节点视为已办结：其期限记录同步置为完成，超期标记随之消失
        deadlineService.completeByStatus(entity.getId(), from);
        // 进入的节点按自身时限（工作日）生成期限记录，期限中心与临近超期据此计算
        deadlineService.enterNode(entity, target);
    }

    private void finishWorkflow(Long caseId) {
        WfInstance wf = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getCaseId, caseId)
                .eq(WfInstance::getStatus, "RUNNING")
                .last("LIMIT 1"));
        if (wf != null) {
            wf.setStatus("COMPLETED");
            wf.setEndTime(LocalDateTime.now());
            wfInstanceMapper.updateById(wf);
        }
    }

    private void logStatus(Long caseId, String from, String to, String action, String opinion) {
        CaseStatusLog log = new CaseStatusLog();
        log.setCaseId(caseId);
        // 首次建档没有前置状态，用 NONE 占位（from_status 为 NOT NULL）
        log.setFromStatus(from == null ? "NONE" : from);
        log.setToStatus(to);
        log.setAction(action);
        log.setOperatorId(UserContext.userId());
        log.setOpinion(opinion);
        log.setOperateTime(LocalDateTime.now());
        statusLogMapper.insert(log);
    }

    private List<CaseDetailVO.ActionVO> buildActions(CaseMain entity, LoginUser current) {
        List<CaseDetailVO.ActionVO> actions = new ArrayList<>();
        if (stateMachine.isArchived(entity.getArchiveStatus())) {
            return actions;
        }
        if (CaseStateMachine.DRAFT.equals(entity.getStatus())) {
            actions.add(new CaseDetailVO.ActionVO("SUBMIT", "提交立案", false));
            return actions;
        }
        if (!canOperate(entity, current)) {
            return actions;
        }
        for (String next : flowService.nextStatuses(entity.getCaseType(), entity.getStatus())) {
            if (CaseStateMachine.CLOSED.equals(next)) {
                continue;
            }
            actions.add(new CaseDetailVO.ActionVO("TRANSIT:" + next,
                    "推进至" + CaseDict.statusName(next), true));
        }
        if (flowService.canRollback(entity.getCaseType(), entity.getStatus())) {
            actions.add(new CaseDetailVO.ActionVO("ROLLBACK", "退回上一节点", true));
        }
        if (flowService.nextStatuses(entity.getCaseType(), entity.getStatus())
                .contains(CaseStateMachine.CLOSED) && hasApprovePermission(entity)) {
            actions.add(new CaseDetailVO.ActionVO("CLOSE", "结案", true));
        }
        if (CaseStateMachine.CLOSED.equals(entity.getStatus())
                && (current.hasRole(ROLE_SYS_ADMIN) || current.hasRole(ROLE_CASE_ADMIN))) {
            actions.add(new CaseDetailVO.ActionVO("ARCHIVE", "归档", false));
        }
        return actions;
    }

    // ==================== 关联 ====================

    @Transactional(rollbackFor = Exception.class)
    public void addRelation(Long caseId, TransitionRequest.RelationRequest req) {
        CaseMain entity = loadCase(caseId);
        assertEditable(entity);
        assertOperatePermission(entity);

        CaseMain related = caseMainMapper.selectOne(new LambdaQueryWrapper<CaseMain>()
                .eq(CaseMain::getCaseNo, req.getRelatedCaseNo()));
        if (related == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "被关联案件不存在: " + req.getRelatedCaseNo());
        }
        if (related.getId().equals(caseId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能与自身建立关联");
        }
        String relationType = StringUtils.hasText(req.getRelationType()) ? req.getRelationType() : "RELATED";
        // 关联为双向：任一方向已存在就视为重复
        Long exists = relationMapper.selectCount(new LambdaQueryWrapper<CaseRelation>()
                .and(w -> w.and(x -> x.eq(CaseRelation::getCaseId, caseId)
                                .eq(CaseRelation::getRelatedCaseId, related.getId()))
                        .or(x -> x.eq(CaseRelation::getCaseId, related.getId())
                                .eq(CaseRelation::getRelatedCaseId, caseId))));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "该关联关系已存在");
        }
        CaseRelation relation = new CaseRelation();
        relation.setCaseId(caseId);
        relation.setRelatedCaseId(related.getId());
        relation.setRelationType(relationType);
        relation.setRelationDesc(req.getRelationDesc());
        relation.setCreateBy(UserContext.userId());
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleted(0);
        relationMapper.insert(relation);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeRelation(Long caseId, Long relationId) {
        CaseMain entity = loadCase(caseId);
        assertEditable(entity);
        assertOperatePermission(entity);
        CaseRelation relation = relationMapper.selectById(relationId);
        if (relation == null || !relation.getCaseId().equals(caseId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "关联关系不存在");
        }
        relationMapper.deleteById(relationId);
    }

    // ==================== 权限与脱敏 ====================

    public CaseMain loadCase(Long id) {
        CaseMain entity = caseMainMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "案件不存在");
        }
        return entity;
    }

    /** 供文档、协同、期限等模块复用：校验当前用户可访问该案件 */
    public CaseMain requireAccessible(Long caseId) {
        CaseMain entity = loadCase(caseId);
        assertCanView(entity);
        return entity;
    }

    /** 归档只读 */
    private void assertEditable(CaseMain entity) {
        if (stateMachine.isArchived(entity.getArchiveStatus())) {
            throw new BizException(ErrorCode.ALREADY_ARCHIVED);
        }
    }

    private void assertCanView(CaseMain entity) {
        LoginUser current = UserContext.get();
        if (isPureAuditor(current)) {
            throw new BizException(ErrorCode.FORBIDDEN, "审计员仅可查询审计日志");
        }
        if (current.isAllScope()) {
            return;
        }
        if ("DEPT".equals(current.getDataScope())) {
            List<Long> deptIds = deptService.selfAndDescendantIds(current.getDeptId());
            List<String> ids = deptIds.stream().map(String::valueOf).collect(Collectors.toList());
            if (ids.contains(entity.getDepartmentId())) {
                return;
            }
        }
        Long memberCount = caseMemberMapper.selectCount(new LambdaQueryWrapper<CaseMember>()
                .eq(CaseMember::getCaseId, entity.getId())
                .eq(CaseMember::getUserId, current.getUserId()));
        if (memberCount == null || memberCount == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "您不是该案件成员");
        }
    }

    private void assertOperatePermission(CaseMain entity) {
        if (!canOperate(entity, UserContext.get())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限办理该案件");
        }
    }

    private boolean canOperate(CaseMain entity, LoginUser current) {
        if (current.hasRole(ROLE_SYS_ADMIN) || current.hasRole(ROLE_CASE_ADMIN)) {
            return true;
        }
        if (current.getUserId().equals(entity.getHandlerId())
                || current.getUserId().equals(entity.getOwnerId())) {
            return true;
        }
        return current.hasRole(ROLE_DEPT_HEAD)
                && current.getDeptId() != null
                && String.valueOf(current.getDeptId()).equals(entity.getDepartmentId());
    }

    private boolean hasApprovePermission(CaseMain entity) {
        LoginUser current = UserContext.get();
        if (current.hasRole(ROLE_SYS_ADMIN) || current.hasRole(ROLE_CASE_ADMIN)) {
            return true;
        }
        return current.hasRole(ROLE_DEPT_HEAD)
                && current.getDeptId() != null
                && String.valueOf(current.getDeptId()).equals(entity.getDepartmentId());
    }

    /**
     * 敏感字段可见性：管理员/案件管理员可见；部门负责人限本部门；承办人限本人案件。
     * 审计员即使 dataScope=ALL 也不得查看业务敏感字段（数据库设计文档 7.4）。
     */
    public boolean canViewSensitive(CaseMain entity) {
        LoginUser current = UserContext.get();
        if (isPureAuditor(current)) {
            return false;
        }
        if (current.hasRole(ROLE_SYS_ADMIN) || current.hasRole(ROLE_CASE_ADMIN)) {
            return true;
        }
        if (current.hasRole(ROLE_DEPT_HEAD) && current.getDeptId() != null
                && String.valueOf(current.getDeptId()).equals(entity.getDepartmentId())) {
            return true;
        }
        return current.getUserId().equals(entity.getHandlerId())
                || current.getUserId().equals(entity.getOwnerId());
    }

    /** 仅拥有审计员单一角色 */
    private boolean isPureAuditor(LoginUser current) {
        return current.getRoles() != null
                && current.getRoles().size() == 1
                && current.hasRole(ROLE_AUDITOR);
    }

    private String maskedName(CaseMain entity) {
        return canViewSensitive(entity)
                ? entity.getEmployeeName()
                : CryptoUtil.maskName(entity.getEmployeeName());
    }
}
