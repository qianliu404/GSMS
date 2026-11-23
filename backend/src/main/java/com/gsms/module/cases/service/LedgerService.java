package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.BizException;
import com.gsms.common.CryptoUtil;
import com.gsms.common.ErrorCode;
import com.gsms.common.PageResult;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.dto.LedgerQuery;
import com.gsms.module.cases.dto.LedgerVO;
import com.gsms.module.cases.entity.CaseArbitrationExt;
import com.gsms.module.cases.entity.CaseInjuryExt;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.mapper.CaseArbitrationExtMapper;
import com.gsms.module.cases.mapper.CaseInjuryExtMapper;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.support.LedgerColumns;
import com.gsms.module.report.mapper.ReportMapper;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 台账：工伤与劳动仲裁案件统一按厂区 / 班组归集，
 * 工伤侧展示认定、鉴定与三项一次性待遇申领进度，仲裁侧展示仲裁流程与裁决金额。
 * 权限过滤复用 CaseService.applyDataScope，避免与案件列表口径不一致。
 */
@Service
public class LedgerService {

    private static final Set<String> SUBSIDY_STATUS = new HashSet<>(Arrays.asList(
            "NOT_APPLIED", "APPLYING", "RECEIVED", "INELIGIBLE"));

    private static final Map<String, String> RESULT_TYPE_NAME = new LinkedHashMap<>();

    static {
        RESULT_TYPE_NAME.put("AWARD", "裁决");
        RESULT_TYPE_NAME.put("MEDIATION", "调解");
        RESULT_TYPE_NAME.put("WITHDRAW", "撤诉");
    }

    private static final Map<String, String> PERFORM_STATUS_NAME = new LinkedHashMap<>();

    static {
        PERFORM_STATUS_NAME.put("PENDING", "待履行");
        PERFORM_STATUS_NAME.put("DOING", "履行中");
        PERFORM_STATUS_NAME.put("DONE", "已履行");
        PERFORM_STATUS_NAME.put("OVERDUE", "逾期未履行");
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CaseMainMapper caseMainMapper;
    private final CaseInjuryExtMapper injuryExtMapper;
    private final CaseArbitrationExtMapper arbitrationExtMapper;
    private final CaseService caseService;
    private final SysUserMapper userMapper;
    private final AuditService auditService;
    private final ReportMapper reportMapper;

    public LedgerService(CaseMainMapper caseMainMapper, CaseInjuryExtMapper injuryExtMapper,
                         CaseArbitrationExtMapper arbitrationExtMapper,
                         CaseService caseService, SysUserMapper userMapper,
                         AuditService auditService, ReportMapper reportMapper) {
        this.caseMainMapper = caseMainMapper;
        this.injuryExtMapper = injuryExtMapper;
        this.arbitrationExtMapper = arbitrationExtMapper;
        this.caseService = caseService;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.reportMapper = reportMapper;
    }

    public PageResult<LedgerVO> page(LedgerQuery query) {
        LambdaQueryWrapper<CaseMain> wrapper = baseWrapper(query);

        caseService.applyDataScope(wrapper);
        wrapper.orderByDesc(CaseMain::getOccurDate);

        Page<CaseMain> page = caseMainMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page, toVOList(page.getRecords()));
    }

    /** 单次导出行数上限，与案件清单导出保持一致 */
    private static final int MAX_EXPORT_ROWS = 5000;

    /**
     * 导出取数：一次查询取到上限条数。
     * 原实现按 100 条/页循环调用 page()，每页都会重跑一次全量 count(*) 与三张表的批量装载。
     */
    private List<LedgerVO> listForExport(LedgerQuery query, int maxRows) {
        LambdaQueryWrapper<CaseMain> wrapper = baseWrapper(query);
        caseService.applyDataScope(wrapper);
        wrapper.orderByDesc(CaseMain::getOccurDate);
        // maxRows 来自服务端常量，非用户输入
        wrapper.last("LIMIT " + Math.max(1, maxRows));
        return toVOList(caseMainMapper.selectList(wrapper));
    }

    /** 批量装载扩展表与承办人姓名后转 VO，分页与导出共用，避免两处口径漂移 */
    private List<LedgerVO> toVOList(List<CaseMain> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> caseIds = records.stream().map(CaseMain::getId).collect(Collectors.toList());
        Map<Long, CaseInjuryExt> injuryMap = injuryExtMapper.selectList(
                        new LambdaQueryWrapper<CaseInjuryExt>().in(CaseInjuryExt::getCaseId, caseIds))
                .stream().collect(Collectors.toMap(CaseInjuryExt::getCaseId, Function.identity(), (a, b) -> a));
        Map<Long, CaseArbitrationExt> arbitrationMap = arbitrationExtMapper.selectList(
                        new LambdaQueryWrapper<CaseArbitrationExt>().in(CaseArbitrationExt::getCaseId, caseIds))
                .stream().collect(Collectors.toMap(CaseArbitrationExt::getCaseId, Function.identity(), (a, b) -> a));
        Map<String, String> userNames = loadUserNames(records);

        List<LedgerVO> list = new ArrayList<>(records.size());
        for (CaseMain entity : records) {
            list.add(toVO(entity, injuryMap.get(entity.getId()), arbitrationMap.get(entity.getId()), userNames));
        }
        return list;
    }

    /** 列表与导出的公共筛选条件 */
    private LambdaQueryWrapper<CaseMain> baseWrapper(LedgerQuery query) {
        LambdaQueryWrapper<CaseMain> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getCaseType())) {
            wrapper.eq(CaseMain::getCaseType, query.getCaseType());
        } else {
            wrapper.in(CaseMain::getCaseType, CaseStateMachine.TYPE_INJURY, CaseStateMachine.TYPE_ARBITRATION);
        }

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(CaseMain::getCaseNo, kw)
                    .or().like(CaseMain::getEmployeeName, kw)
                    .or().like(CaseMain::getWorkshopTeam, kw)
                    .or().apply("id IN (SELECT case_id FROM case_arbitration_ext WHERE arbitration_no LIKE {0})",
                            "%" + kw + "%"));
        }
        wrapper.eq(StringUtils.hasText(query.getPlantArea()), CaseMain::getPlantArea, query.getPlantArea());

        // 认定状态与申领状态在扩展表，条件下推到 SQL，避免捞全表后在内存过滤
        if (StringUtils.hasText(query.getRecognizeResult())) {
            wrapper.apply("id IN (SELECT case_id FROM case_injury_ext WHERE recognize_result = {0})",
                    query.getRecognizeResult());
        }
        if (StringUtils.hasText(query.getSubsidyStatus())) {
            wrapper.apply("id IN (SELECT case_id FROM case_injury_ext "
                            + "WHERE subsidy_disability_status = {0} OR subsidy_medical_status = {0} "
                            + "OR subsidy_employment_status = {0})",
                    query.getSubsidyStatus());
        }
        return wrapper;
    }

    /** 台账顶部统计卡：案件数、待认定数、待鉴定数、待遇未结清数 */
    public Map<String, Object> summary() {
        // 统计口径下推到 SQL 一次算完，避免把整表案件与扩展信息捞进内存再遍历
        CaseService.DataScope scope = caseService.dataScope();
        Map<String, Object> row = reportMapper.ledgerSummary(scope.allScope, scope.userId, scope.deptIds);
        long total = row == null ? 0 : toLong(row.get("total"));
        if (total == 0) {
            return Collections.singletonMap("total", 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (int) total);
        result.put("injuryCount", (int) toLong(row.get("injuryCount")));
        result.put("arbitrationCount", (int) toLong(row.get("arbitrationCount")));
        result.put("areaCount", (int) toLong(row.get("areaCount")));
        result.put("waitRecognize", (int) toLong(row.get("waitRecognize")));
        result.put("waitAppraisal", (int) toLong(row.get("waitAppraisal")));
        result.put("unpaid", (int) toLong(row.get("unpaid")));
        return result;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 更新某一项一次性待遇的申领状态与金额 */
    @Transactional(rollbackFor = Exception.class)
    public void updateSubsidy(Long caseId, String key, String status, BigDecimal amount) {
        if (!SUBSIDY_STATUS.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的申领状态: " + status);
        }
        CaseMain entity = caseService.requireAccessible(caseId);
        if (!CaseStateMachine.TYPE_INJURY.equals(entity.getCaseType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅工伤案件有一次性待遇申领记录");
        }
        if (!caseService.canViewSensitive(entity)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改待遇金额");
        }

        CaseInjuryExt ext = injuryExtMapper.selectById(caseId);
        if (ext == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工伤扩展信息不存在");
        }
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        switch (key) {
            case "DISABILITY":
                ext.setSubsidyDisabilityStatus(status);
                ext.setSubsidyDisabilityAmount(value);
                break;
            case "MEDICAL":
                ext.setSubsidyMedicalStatus(status);
                ext.setSubsidyMedicalAmount(value);
                break;
            case "EMPLOYMENT":
                ext.setSubsidyEmploymentStatus(status);
                ext.setSubsidyEmploymentAmount(value);
                break;
            default:
                throw new BizException(ErrorCode.PARAM_INVALID, "未知的待遇项: " + key);
        }
        injuryExtMapper.updateById(ext);
    }

    /* ==================== 导出 ==================== */

    /** 导出台账：列与顺序由调用方按当前显示字段传入，表头与列表共用同一套列定义 */
    public Map<String, Object> exportData(LedgerQuery query, String columnKeys) {
        List<LedgerColumns.Column> columns = LedgerColumns.resolve(columnKeys);
        List<LedgerVO> records = listForExport(query, MAX_EXPORT_ROWS);
        List<List<Object>> rows = new ArrayList<>(records.size());

        int seq = 0;
        for (LedgerVO vo : records) {
            List<Object> row = new ArrayList<>(columns.size());
            int rowNo = ++seq;
            for (LedgerColumns.Column column : columns) {
                row.add("seq".equals(column.getKey()) ? rowNo : cellOf(vo, column.getKey()));
            }
            rows.add(row);
        }

        List<String> headers = columns.stream()
                .map(LedgerColumns.Column::getLabel).collect(Collectors.toList());
        List<Integer> widths = columns.stream()
                .map(LedgerColumns.Column::getWidth).collect(Collectors.toList());

        auditService.success("台账", "导出台账", null,
                "共 " + rows.size() + " 条，字段=" + String.join(",", headers));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("widths", widths);
        result.put("rows", rows);
        return result;
    }

    /** 单元格取值：列表怎么显示，导出就怎么给 */
    private Object cellOf(LedgerVO vo, String key) {
        boolean injury = CaseStateMachine.TYPE_INJURY.equals(vo.getCaseType());
        switch (key) {
            case "type":
                return vo.getCaseTypeName();
            case "caseNo":
                return vo.getCaseNo();
            case "plantArea":
                return dash(vo.getPlantArea());
            case "workshopTeam":
                return dash(StringUtils.hasText(vo.getWorkshopTeam()) ? vo.getWorkshopTeam() : vo.getDepartmentName());
            case "employeeName":
                return dash(vo.getEmployeeName());
            case "occurDate":
                return dateText(vo.getOccurDate());
            case "description":
                return dash(vo.getDescription());
            case "restDays":
                return injury ? (vo.getRestDays() == null ? "待登记" : vo.getRestDays() + " 天") : "—";
            case "performAmount":
                if (injury) {
                    return "—";
                }
                return vo.getPerformAmount() == null ? "待登记" : vo.getPerformAmount();
            case "injuryDesc":
                return injury ? injuryDesc(vo) : "—";
            case "disputeReason":
                return injury ? "—" : dash(vo.getDisputeReason());
            case "leaderL1":
                return dash(vo.getLeaderL1());
            case "leaderL2":
                return dash(vo.getLeaderL2());
            case "recognize":
                return injury ? vo.getRecognizeResultName() : "—";
            case "appraisal":
                return injury ? appraisalText(vo.getAppraisalLevel()) : "—";
            case "subsidyDisability":
                return injury ? subsidyText(vo, vo.getSubsidyDisabilityStatus(), vo.getSubsidyDisabilityAmount())
                        : "—";
            case "subsidyMedical":
                return injury ? subsidyText(vo, vo.getSubsidyMedicalStatus(), vo.getSubsidyMedicalAmount()) : "—";
            case "subsidyEmployment":
                return injury ? subsidyText(vo, vo.getSubsidyEmploymentStatus(), vo.getSubsidyEmploymentAmount())
                        : "—";
            case "arbitrationNo":
                return injury ? "—" : dash(vo.getArbitrationNo());
            case "applyDate":
                return injury ? "—" : dateText(vo.getApplyDate());
            case "acceptDate":
                return injury ? "—" : dateText(vo.getAcceptDate());
            case "hearingDate":
                return injury ? "—" : dash(vo.getHearingDate());
            case "resultType":
                return dash(vo.getResultTypeName());
            case "awardAmount":
                if (injury) {
                    return "—";
                }
                return vo.getAwardAmount() == null ? "—" : vo.getAwardAmount();
            case "performStatus":
                return injury ? "—" : dash(vo.getPerformStatusName());
            case "handlerName":
                return dash(vo.getHandlerName());
            case "statusName":
                return dash(vo.getStatusName());
            case "closeDate":
                return dash(vo.getCloseDate());
            default:
                return "";
        }
    }

    private String injuryDesc(LedgerVO vo) {
        StringBuilder desc = new StringBuilder();
        if (vo.getInjuryDate() != null) {
            desc.append(vo.getInjuryDate().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        }
        if (StringUtils.hasText(vo.getInjuryReason())) {
            if (desc.length() > 0) {
                desc.append('，');
            }
            desc.append(vo.getInjuryReason().trim());
        }
        return desc.length() == 0 ? "—" : desc.toString();
    }

    private String appraisalText(String level) {
        if (!StringUtils.hasText(level) || "—".equals(level)) {
            return "待鉴定";
        }
        return level.endsWith("级") ? level + " 伤残" : level;
    }

    private String subsidyText(LedgerVO vo, String status, BigDecimal amount) {
        String name = CaseDict.subsidyStatusName(status);
        if (vo.isAmountMasked() || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return name;
        }
        return name + "（￥" + String.format("%,.2f", amount) + "）";
    }

    private String dateText(java.time.LocalDate date) {
        return date == null ? "—" : date.format(DATE);
    }

    private String dash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private Map<String, String> loadUserNames(List<CaseMain> records) {
        Set<String> ids = records.stream().map(CaseMain::getHandlerId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, ids))
                .stream().collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));
    }

    private LedgerVO toVO(CaseMain entity, CaseInjuryExt ext, CaseArbitrationExt arbitration,
                          Map<String, String> userNames) {
        boolean sensitive = caseService.canViewSensitive(entity);
        boolean injury = CaseStateMachine.TYPE_INJURY.equals(entity.getCaseType());
        LedgerVO vo = new LedgerVO();
        vo.setId(entity.getId());
        vo.setCaseType(entity.getCaseType());
        vo.setCaseTypeName(injury ? "工伤" : "仲裁");
        vo.setCaseNo(entity.getCaseNo());
        vo.setPlantArea(entity.getPlantArea());
        vo.setWorkshopTeam(entity.getWorkshopTeam());
        vo.setDepartmentName(entity.getDepartmentName());
        vo.setEmployeeName(sensitive ? entity.getEmployeeName()
                : CryptoUtil.maskName(entity.getEmployeeName()));
        vo.setLeaderL1(entity.getLeaderL1());
        vo.setLeaderL2(entity.getLeaderL2());
        vo.setInjuryDate(entity.getOccurDate());
        vo.setOccurDate(entity.getOccurDate());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setStatusName(CaseDict.statusName(entity.getStatus()));
        vo.setAmountMasked(!sensitive);
        vo.setHandlerName(userNames.get(entity.getHandlerId()));
        vo.setCloseTypeName(CaseDict.closeTypeName(entity.getCloseType()));
        vo.setCloseDate(entity.getCloseDate() == null ? null : entity.getCloseDate().format(DATE));

        if (injury) {
            if (ext != null) {
                vo.setInjuryReason(ext.getInjuryReason());
                if (ext.getInjuryDate() != null) {
                    vo.setInjuryDate(ext.getInjuryDate());
                }
                vo.setRecognizeResult(ext.getRecognizeResult());
                vo.setRecognizeResultName(CaseDict.recognizeName(ext.getRecognizeResult()));
                vo.setAppraisalLevel(ext.getAppraisalLevel());
                vo.setRestDays(ext.getRestDays());
                vo.setSubsidyDisabilityStatus(ext.getSubsidyDisabilityStatus());
                vo.setSubsidyMedicalStatus(ext.getSubsidyMedicalStatus());
                vo.setSubsidyEmploymentStatus(ext.getSubsidyEmploymentStatus());
                if (sensitive) {
                    vo.setSubsidyDisabilityAmount(ext.getSubsidyDisabilityAmount());
                    vo.setSubsidyMedicalAmount(ext.getSubsidyMedicalAmount());
                    vo.setSubsidyEmploymentAmount(ext.getSubsidyEmploymentAmount());
                    vo.setSubsidyTotal(nvl(ext.getSubsidyDisabilityAmount())
                            .add(nvl(ext.getSubsidyMedicalAmount()))
                            .add(nvl(ext.getSubsidyEmploymentAmount())));
                }
            } else {
                vo.setRecognizeResultName(CaseDict.recognizeName(null));
            }
        }

        vo.setDisputeReason(entity.getDescription());
        if (arbitration != null) {
            vo.setArbitrationNo(arbitration.getArbitrationNo());
            vo.setApplyDate(arbitration.getApplyDate());
            vo.setAcceptDate(arbitration.getAcceptDate());
            vo.setHearingDate(arbitration.getHearingDate() == null ? null
                    : arbitration.getHearingDate().format(DATE_TIME));
            vo.setResultType(arbitration.getResultType());
            vo.setResultTypeName(arbitration.getResultType() == null ? null
                    : RESULT_TYPE_NAME.getOrDefault(arbitration.getResultType(), arbitration.getResultType()));
            vo.setPerformStatus(arbitration.getPerformStatus());
            vo.setPerformStatusName(arbitration.getPerformStatus() == null ? null
                    : PERFORM_STATUS_NAME.getOrDefault(arbitration.getPerformStatus(),
                    arbitration.getPerformStatus()));
            vo.setPerformDate(arbitration.getPerformDate());
            if (sensitive) {
                vo.setAwardAmount(arbitration.getAwardAmount());
                vo.setPerformAmount(arbitration.getPerformAmount());
            }
        }
        return vo;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
