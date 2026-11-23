package com.gsms.module.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.dto.CaseQuery;
import com.gsms.module.cases.dto.CaseVO;
import com.gsms.module.cases.entity.CaseInjuryExt;
import com.gsms.module.cases.mapper.CaseInjuryExtMapper;
import com.gsms.module.cases.service.CaseDict;
import com.gsms.module.cases.service.CaseService;
import com.gsms.module.report.dto.CaseExportRow;
import com.gsms.module.report.dto.InjuryStatRow;
import com.gsms.module.report.dto.ReportDTO;
import com.gsms.module.report.mapper.ReportMapper;
import com.gsms.module.system.service.DeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportService {

    private static final String ROLE_AUDITOR = "ROLE_AUDITOR";

    private final ReportMapper reportMapper;
    private final DeptService deptService;
    private final CaseService caseService;
    private final AuditService auditService;
    private final CaseInjuryExtMapper injuryExtMapper;

    public ReportService(ReportMapper reportMapper, DeptService deptService,
                         CaseService caseService, AuditService auditService,
                         CaseInjuryExtMapper injuryExtMapper) {
        this.reportMapper = reportMapper;
        this.deptService = deptService;
        this.caseService = caseService;
        this.auditService = auditService;
        this.injuryExtMapper = injuryExtMapper;
    }

    /** 报表数据范围参数 */
    private static class Scope {
        boolean allScope;
        String userId;
        List<String> deptIds;
    }

    private Scope currentScope() {
        LoginUser current = UserContext.get();
        Scope scope = new Scope();
        // 纯审计员不参与业务报表
        boolean pureAuditor = current.getRoles() != null && current.getRoles().size() == 1
                && current.hasRole(ROLE_AUDITOR);
        scope.allScope = current.isAllScope() && !pureAuditor;
        scope.userId = pureAuditor ? "__none__" : current.getUserId();
        if (!scope.allScope && "DEPT".equals(current.getDataScope())) {
            scope.deptIds = deptService.selfAndDescendantIds(current.getDeptId()).stream()
                    .map(String::valueOf).collect(Collectors.toList());
        }
        return scope;
    }

    public ReportDTO.CaseSummary caseSummary() {
        Scope scope = currentScope();
        ReportDTO.CaseSummary summary = new ReportDTO.CaseSummary();

        List<Map<String, Object>> byType = reportMapper.countByType(
                scope.allScope, scope.userId, scope.deptIds);
        List<Map<String, Object>> byStatus = reportMapper.countByStatus(
                scope.allScope, scope.userId, scope.deptIds);
        List<Map<String, Object>> monthly = reportMapper.countByMonth(
                scope.allScope, scope.userId, scope.deptIds, 12);

        long total = 0;
        for (Map<String, Object> row : byType) {
            String name = String.valueOf(row.get("name"));
            long value = toLong(row.get("value"));
            summary.getByType().add(new ReportDTO.ChartItem(CaseDict.typeName(name), value));
            total += value;
        }
        for (Map<String, Object> row : byStatus) {
            String name = String.valueOf(row.get("name"));
            long value = toLong(row.get("value"));
            summary.getByStatus().add(new ReportDTO.ChartItem(CaseDict.statusName(name), value));
            if ("CLOSED".equals(name)) {
                summary.setClosedCount(value);
            } else if ("ARCHIVED".equals(name)) {
                summary.setArchivedCount(value);
            }
        }
        for (Map<String, Object> row : monthly) {
            summary.getMonthly().add(new ReportDTO.ChartItem(
                    String.valueOf(row.get("name")), toLong(row.get("value"))));
        }
        summary.setTotal(total);
        summary.setOpenCount(total - summary.getClosedCount() - summary.getArchivedCount());
        return summary;
    }

    public ReportDTO.Efficiency efficiency() {
        Scope scope = currentScope();
        ReportDTO.Efficiency vo = new ReportDTO.Efficiency();
        Double avg = reportMapper.avgCloseDays(scope.allScope, scope.userId, scope.deptIds);
        vo.setAvgCloseDays(avg == null ? null : Math.round(avg * 10) / 10.0);

        Map<String, Object> task = reportMapper.taskStat(scope.allScope, scope.userId, scope.deptIds);
        long taskTotal = task == null ? 0 : toLong(task.get("total"));
        long taskDone = task == null ? 0 : toLong(task.get("done"));
        long taskOverdue = task == null ? 0 : toLong(task.get("overdue"));
        vo.setTaskTotal(taskTotal);
        vo.setTaskDone(taskDone);
        vo.setTaskOverdue(taskOverdue);
        vo.setTaskDoneRate(taskTotal == 0 ? 0 : Math.round(taskDone * 1000.0 / taskTotal) / 10.0);
        return vo;
    }

    public ReportDTO.Amount amount() {
        Scope scope = currentScope();
        ReportDTO.Amount vo = new ReportDTO.Amount();
        LoginUser current = UserContext.get();
        // 协办人/会签人/阅知人不展示金额汇总
        boolean masked = !scope.allScope
                && !current.hasRole("ROLE_HANDLER") && !current.hasRole("ROLE_DEPT_HEAD");
        if (masked) {
            vo.setMasked(true);
            return vo;
        }
        BigDecimal injury = reportMapper.sumInjuryAmount(scope.allScope, scope.userId, scope.deptIds);
        BigDecimal award = reportMapper.sumAwardAmount(scope.allScope, scope.userId, scope.deptIds);
        vo.setInjuryTotal(injury == null ? BigDecimal.ZERO : injury);
        vo.setArbitrationTotal(award == null ? BigDecimal.ZERO : award);
        vo.setTotal(vo.getInjuryTotal().add(vo.getArbitrationTotal()));
        return vo;
    }

    public ReportDTO.Overdue overdue() {
        Scope scope = currentScope();
        ReportDTO.Overdue vo = new ReportDTO.Overdue();
        Map<String, Object> stat = reportMapper.deadlineStat(scope.allScope, scope.userId, scope.deptIds);
        long total = stat == null ? 0 : toLong(stat.get("total"));
        long overdue = stat == null ? 0 : toLong(stat.get("overdue"));
        long done = stat == null ? 0 : toLong(stat.get("done"));
        vo.setDeadlineTotal(total);
        vo.setDeadlineOverdue(overdue);
        vo.setDeadlineDone(done);
        vo.setOverdueRate(total == 0 ? 0 : Math.round(overdue * 1000.0 / total) / 10.0);
        return vo;
    }

    /** 导出数据包：案件清单明细 + 工伤事故人员信息统计表 */
    public static class ExportBundle {
        private final List<CaseExportRow> rows;
        private final List<InjuryStatRow> stats;
        private final int year;

        ExportBundle(List<CaseExportRow> rows, List<InjuryStatRow> stats, int year) {
            this.rows = rows;
            this.stats = stats;
            this.year = year;
        }

        public List<CaseExportRow> getRows() {
            return rows;
        }

        public List<InjuryStatRow> getStats() {
            return stats;
        }

        public int getYear() {
            return year;
        }
    }

    /**
     * 导出案件：按当前筛选条件与数据权限一次性取数，
     * 同时产出「案件清单」与「工伤事故人员信息统计表」两份数据，避免重复分页查询。
     * 每行携带导出人便于外发追溯。
     */
    public ExportBundle buildExport(CaseQuery query, String exportBy) {
        List<CaseVO> cases = collectCases(query);
        List<CaseExportRow> rows = toRows(cases, exportBy);
        List<InjuryStatRow> stats = toStatRows(cases);
        auditService.success("报表中心", "导出案件清单", null,
                "rows=" + rows.size() + ", 工伤统计=" + stats.size());
        return new ExportBundle(rows, stats, LocalDate.now().getYear());
    }

    /** 归档卷宗目录：仅按当前筛选导出案件基础信息 */
    public List<CaseExportRow> archiveRows(CaseQuery query, String exportBy) {
        List<CaseExportRow> rows = toRows(collectCases(query), exportBy);
        auditService.success("归档库", "导出卷宗目录", null, "rows=" + rows.size());
        return rows;
    }

    private List<CaseExportRow> toRows(List<CaseVO> cases, String exportBy) {
        String exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<CaseExportRow> rows = new ArrayList<>(cases.size());
        for (CaseVO vo : cases) {
            CaseExportRow row = new CaseExportRow();
            row.setCaseNo(vo.getCaseNo());
            row.setCaseName(vo.getCaseName());
            row.setCaseTypeName(vo.getCaseTypeName());
            row.setEmployeeName(vo.getEmployeeName());
            row.setDepartmentName(vo.getDepartmentName());
            row.setOccurDate(vo.getOccurDate() == null ? "" : vo.getOccurDate().toString());
            row.setStatusName(vo.getStatusName());
            row.setHandlerName(vo.getHandlerName() == null ? vo.getHandlerId() : vo.getHandlerName());
            row.setCloseDate(vo.getCloseDate() == null ? "" : vo.getCloseDate().toString());
            row.setExportBy(exportBy);
            row.setExportTime(exportTime);
            rows.add(row);
        }
        return rows;
    }

    /** 按条件与数据权限一次取到导出上限，避免分页循环里每页重复 count(*) */
    private List<CaseVO> collectCases(CaseQuery query) {
        return caseService.listForExport(query, CaseService.MAX_EXPORT_ROWS);
    }

    /** 工伤事故人员信息统计表：仅工伤案件，字段取自台账扩展信息 */
    private List<InjuryStatRow> toStatRows(List<CaseVO> cases) {
        List<CaseVO> injuries = cases.stream()
                .filter(vo -> "INJURY".equals(vo.getCaseType()))
                .collect(Collectors.toList());
        if (injuries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> caseIds = injuries.stream().map(CaseVO::getId).collect(Collectors.toList());
        Map<Long, CaseInjuryExt> extMap = injuryExtMapper.selectList(
                        new LambdaQueryWrapper<CaseInjuryExt>().in(CaseInjuryExt::getCaseId, caseIds))
                .stream().collect(Collectors.toMap(CaseInjuryExt::getCaseId, Function.identity(), (a, b) -> a));

        List<InjuryStatRow> stats = new ArrayList<>(injuries.size());
        int sortNo = 0;
        for (CaseVO vo : injuries) {
            CaseInjuryExt ext = extMap.get(vo.getId());
            InjuryStatRow row = new InjuryStatRow();
            row.setSortNo(++sortNo);
            row.setPlantArea(dash(vo.getPlantArea()));
            row.setWorkshopTeam(dash(vo.getWorkshopTeam()));
            row.setEmployeeName(dash(vo.getEmployeeName()));
            row.setInjuryDesc(injuryDesc(vo, ext));
            row.setLeaderL1(dash(vo.getLeaderL1()));
            row.setLeaderL2(dash(vo.getLeaderL2()));
            row.setRecognizeName(CaseDict.recognizeName(ext == null ? null : ext.getRecognizeResult()));
            row.setAppraisalLevel(ext == null || !StringUtils.hasText(ext.getAppraisalLevel())
                    ? "未鉴定" : ext.getAppraisalLevel());
            row.setSubsidyDisability(subsidy(ext, "DISABILITY"));
            row.setSubsidyMedical(subsidy(ext, "MEDICAL"));
            row.setSubsidyEmployment(subsidy(ext, "EMPLOYMENT"));
            stats.add(row);
        }
        return stats;
    }

    /** 受伤原因及日期：2026年01月08日，<受伤原因> */
    private String injuryDesc(CaseVO vo, CaseInjuryExt ext) {
        LocalDate date = ext != null && ext.getInjuryDate() != null ? ext.getInjuryDate() : vo.getOccurDate();
        String reason = ext == null ? null : ext.getInjuryReason();
        StringBuilder desc = new StringBuilder();
        if (date != null) {
            desc.append(date.getYear()).append('年')
                    .append(String.format("%02d", date.getMonthValue())).append('月')
                    .append(String.format("%02d", date.getDayOfMonth())).append('日');
        }
        if (StringUtils.hasText(reason)) {
            if (desc.length() > 0) {
                desc.append('，');
            }
            desc.append(reason.trim());
        }
        return desc.length() == 0 ? "—" : desc.toString();
    }

    private String subsidy(CaseInjuryExt ext, String item) {
        if (ext == null) {
            return CaseDict.subsidyStatusName(null);
        }
        String code;
        switch (item) {
            case "DISABILITY":
                code = ext.getSubsidyDisabilityStatus();
                break;
            case "MEDICAL":
                code = ext.getSubsidyMedicalStatus();
                break;
            default:
                code = ext.getSubsidyEmploymentStatus();
                break;
        }
        return CaseDict.subsidyStatusName(code);
    }

    private String dash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
